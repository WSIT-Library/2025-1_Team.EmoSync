from deepface import DeepFace
import cv2
import time

from pydub import AudioSegment
from diffusers import BitsAndBytesConfig, SD3Transformer2DModel
from diffusers import StableDiffusion3Pipeline
import torch
import random
import numpy as np
import scipy.io.wavfile
from transformers import AutoProcessor, MusicgenForConditionalGeneration
from fastapi import FastAPI, UploadFile, File
from pydantic import BaseModel
import os
from deepface import DeepFace
from transformers import AutoProcessor, MusicgenForConditionalGeneration
from diffusers import StableDiffusion3Pipeline, BitsAndBytesConfig, SD3Transformer2DModel
import scipy.io.wavfile
from diffusers import StableDiffusionXLPipeline
import librosa
import sounddevice as sd
import soundfile as sf
from transformers import AutoModelForAudioClassification, AutoFeatureExtractor
from google.cloud import storage
from google.oauth2 import service_account
import tempfile
import subprocess
import requests, io



# 표정 분석 및 감정 도출 함수
def func_deepface(FileRoot, user_fileName, user_filePath):
    # DeepFace를 이용해 감정 분석 결과 출력
    try:
        result = DeepFace.analyze(img_path = FileRoot + user_filePath + user_fileName, actions=["emotion"])
        print("deepface 분석 결과: ", result)
        emotions = result[0]['emotion']
        return emotions
    except ValueError as e:
        print("얼굴이 감지되지 않았습니다:", e)
        return "none"

# 음성 분석
def func_SER(FileRoot, audio_relative_path, filename):
    MODEL_ID = "firdhokk/speech-emotion-recognition-with-openai-whisper-large-v3"
    model = AutoModelForAudioClassification.from_pretrained(MODEL_ID)
    feature_extractor = AutoFeatureExtractor.from_pretrained(MODEL_ID, do_normalize=True)
    id2label = model.config.id2label

    # def preprocess_audio(audio_path: str, max_duration: float = 30.0):
    #     audio_array, sr = librosa.load(audio_path, sr=feature_extractor.sampling_rate)
    #     max_length = int(sr * max_duration)
    #     if len(audio_array) > max_length:
    #         audio_array = audio_array[:max_length]
    #     else:
    #         audio_array = np.pad(audio_array, (0, max_length - len(audio_array)))
    #     return feature_extractor(
    #         audio_array,
    #         sampling_rate=sr,
    #         max_length=max_length,
    #         truncation=True,
    #         return_tensors="pt"
    #     )

    def preprocess_audio(audio_path: str, max_duration: float = 30.0):
        """
        • audio_path가 로컬 파일 경로(.wav, .flac 등)라면 그대로 librosa.load → feature_extractor 진행.
        • audio_path가 HTTP/HTTPS URL(.webm)이라면:
        1) requests로 원격 파일을 임시(webm)으로 다운로드
        2) ffmpeg로 그 임시 웹엠을 WAV로 변환(샘플레이트=feature_extractor.sampling_rate, 모노)
        3) 변환된 WAV를 librosa.load → feature_extractor 진행
        4) 임시 파일들(cleanup)
        """
        # 1) URL 체크 (http 또는 https 로 시작하면 원격 파일로 간주)
        is_remote = audio_path.startswith("http://") or audio_path.startswith("https://")

        if is_remote:
            # 2) 임시 웹엠 파일 생성
            tmp_webm = tempfile.NamedTemporaryFile(suffix=".webm", delete=False)
            try:
                # Download remote file
                with requests.get(audio_path, stream=True) as response:
                    response.raise_for_status()
                    for chunk in response.iter_content(chunk_size=8192):
                        if chunk:
                            tmp_webm.write(chunk)
                tmp_webm.flush()
                tmp_webm_path = tmp_webm.name
            finally:
                tmp_webm.close()

            # 3) 임시 WAV 파일 경로 생성
            tmp_wav = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
            tmp_wav_path = tmp_wav.name
            tmp_wav.close()

            # 4) ffmpeg 로 webm → wav 변환
            #    - 오디오 트랙만 추출, 모노(1채널), 샘플레이트 = feature_extractor.sampling_rate
            sr = feature_extractor.sampling_rate
            ffmpeg_cmd = [
                "ffmpeg",
                "-y",                     # 덮어쓰기 허용
                "-i", tmp_webm_path,      # 입력 웹엠
                "-ac", "1",               # 모노
                "-ar", str(sr),           # 샘플레이트
                tmp_wav_path              # 출력 wav
            ]
            # ffmpeg이 시스템 PATH에 있어야 합니다.
            subprocess.run(ffmpeg_cmd, check=True)

            # 변환된 tmp_wav_path를 로컬 파일로 처리하기 위해 audio_path를 변경
            local_audio_path = tmp_wav_path
        else:
            # 로컬 파일일 때
            local_audio_path = audio_path

        try:
            # 5) librosa.load → feature_extractor 로직(기존과 동일)
            audio_array, sr = librosa.load(local_audio_path, sr=feature_extractor.sampling_rate)
            max_length = int(sr * max_duration)
            if len(audio_array) > max_length:
                audio_array = audio_array[:max_length]
            else:
                audio_array = np.pad(audio_array, (0, max_length - len(audio_array)))

            # feature_extractor에 넣어서 tensor 생성
            return feature_extractor(
                audio_array,
                sampling_rate=sr,
                max_length=max_length,
                truncation=True,
                return_tensors="pt"
            )

        finally:
            # 6) 임시 파일(cleanup)
            if is_remote:
                try:
                    os.remove(tmp_webm_path)
                except OSError:
                    pass
                try:
                    os.remove(tmp_wav_path)
                except OSError:
                    pass
    
    def predict_emotion_scores(audio_path: str):
        """
        모든 감정에 대한 확률을 반환합니다.
        """
        print("[감정 분석 중]...")
        inputs = preprocess_audio(audio_path)
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        model.to(device)
        inputs = {k: v.to(device) for k, v in inputs.items()}

        with torch.no_grad():
            outputs = model(**inputs)
        logits = outputs.logits[0]  # shape: (num_labels,)

        # 1) 로짓 그대로 보기
        raw_scores = {id2label[i]: float(logits[i].cpu()) for i in range(len(logits))}

        # 2) 확률로 변환하기 (softmax)
        probs = torch.softmax(logits, dim=-1)
        prob_scores = {
        id2label[i]: round(float(probs[i].cpu()) * 100, 2)
        for i in range(len(probs))
        }

        return prob_scores
    
    prob_scores = predict_emotion_scores(FileRoot+audio_relative_path+filename)

    return prob_scores


# 표정과 음성 분석 값을 통합
def emotion_integration(deepface_result, SER_result):

    # 1. face 딕셔너리의 np.float32를 float로 변환
    deepface_result = {key: float(value) for key, value in deepface_result.items()}

    # 2. voice 딕셔너리의 키를 face 딕셔너리의 키와 동일하게 변경
    # 'fearful' -> 'fear', 'surprised' -> 'surprise'로 변경
    SER_result = {key.replace('fearful', 'fear').replace('surprised', 'surprise'): value for key, value in SER_result.items()}

    # 3. 두 딕셔너리에서 동일한 키에 대해 평균값을 계산
    average_values = {key: (deepface_result[key] + SER_result[key]) / 2 for key in deepface_result if key in SER_result}
    print(f"*****분석한 감정 값: {average_values}")

    return average_values


# deepface 로 결과값을 받아 감정을 도출해내는 함수
def map_emotion(emotions):
    # 1. 감정값 추출 (0~100)
    angry = emotions.get('angry', 0)    # 화남
    disgust = emotions.get('disgust', 0) # 혐오
    fear = emotions.get('fear', 0)       # 두려움
    happy = emotions.get('happy', 0)     # 행복
    sad = emotions.get('sad', 0)         # 슬픔
    surprise = emotions.get('surprise', 0) # 놀람
    neutral = emotions.get('neutral', 0)  # 중립

    # 2. 세분화된 감정 상태를 정의하는 조건들 (0~100 기준 비교)
    if happy > 60 and surprise > 15: 
        return 'excited', "기쁨과 놀람이 섞여서 '흥분'된 상태"
    elif happy > 50 and neutral > 20: 
        return 'content', "행복하고 중립적인 상태에서 '만족'한 상태"
    elif surprise > 25 and neutral > 25:
        return 'curious', "놀람과 중립적인 상태에서 '호기심'을 느끼는 상태"
    elif happy > 40 and surprise > 10:
        return 'amused', "웃긴 상황에서 '재미있어 하는' 상태"
    elif fear > 15 and sad > 15:
        return 'anxious', "두려움과 슬픔이 결합된 '불안'한 상태"
    elif fear > 25 and surprise > 15:
        return 'nervous', "두려움과 놀람이 결합된 '긴장'한 상태"
    elif angry > 35 and sad > 25:
        return 'frustrated', "화남과 슬픔이 결합된 '좌절'된 상태"
    elif angry > 45 and neutral > 20: 
        return 'irritated', "화가 나고 중립적인 상태에서 '짜증'나는 상태"
    elif sad > 55 and neutral > 20:
        return 'melancholy', "슬픔과 중립적인 상태에서 '우울'한 상태"
    elif sad > 35 and neutral > 30:
        return 'tired', "슬픔과 중립적인 상태에서 '피곤'한 상태"
    elif neutral > 15 and neutral < 50 and happy < 10 and sad < 10 and angry < 10:
        return 'bored', "감정이 거의 없고 지루한 상태"
    elif surprise > 45 and fear > 25:  
        return 'shocked', "놀람과 두려움이 결합된 '충격'받은 상태"
    elif surprise > 25 and fear > 10 and neutral > 20: 
        return 'confused', "놀람과 두려움, 중립적인 상태에서 '혼란'스러운 상태"
    elif happy > 45 and neutral > 30:  
        return 'affectionate', "행복과 중립적인 상태에서 '애정'을 느끼는 상태"
    elif neutral > 45 and (angry > 15 or sad > 15): 
        return 'serious', "중립적인 상태에서 화나거나 슬픈 감정이 있는 '진지'한 상태"
    elif angry > 50 and neutral > 30:  
        return 'determined', "화남과 중립적인 상태에서 '결단'을 내린 상태"

    # 3. 감정 값 50~100 범위에서 세 구간으로 나누어 추가 감정 분류
    # Low 구간 (50-70)
    if happy > 50 and happy <= 70:
        return 'low_happy', "행복하지만 비교적 낮은 강도의 기쁨"
    elif neutral > 50 and neutral <= 70:
        return 'low_neutral', "중립적이고 비교적 낮은 감정"
    elif surprise > 50 and surprise <= 70:
        return 'low_surprise', "놀람이 비교적 낮은 강도의 놀람"
    elif disgust > 50 and disgust <= 70:
        return 'low_disgusted', "혐오감이 비교적 낮은 상태"
    elif sad > 50 and sad <= 70:
        return 'low_sad', "슬픔이 비교적 낮은 강도의 슬픔"
    elif angry > 50 and angry <= 70:
        return 'low_angry', "화남이 비교적 낮은 강도의 화남"
    elif fear > 50 and fear <= 70:
        return 'low_fear', "두려움이 비교적 낮은 강도의 두려움"

    # Middle 구간 (71-85)
    if happy > 70 and happy <= 85:
        return 'middle_happy', "중간 정도의 행복"
    elif neutral > 70 and neutral <= 85:
        return 'middle_neutral', "중간 정도의 중립"
    elif surprise > 70 and surprise <= 85:
        return 'middle_surprise', "중간 정도의 놀람"
    elif disgust > 70 and disgust <= 85:
        return 'middle_disgusted', "중간 정도의 혐오감"
    elif sad > 70 and sad <= 85:
        return 'middle_sad', "중간 정도의 슬픔"
    elif angry > 70 and angry <= 85:
        return 'middle_angry', "중간 정도의 화남"
    elif fear > 70 and fear <= 85:
        return 'middle_fear', "중간 정도의 두려움"

    # High 구간 (86-100)
    if happy > 85:
        return 'high_happy', "매우 강한 행복"
    elif neutral > 85:
        return 'high_neutral', "매우 강한 중립"
    elif surprise > 85:
        return 'high_surprise', "매우 강한 놀람"
    elif disgust > 85:
        return 'high_disgusted', "매우 강한 혐오감"
    elif sad > 85:
        return 'high_sad', "매우 강한 슬픔"
    elif angry > 85:
        return 'high_angry', "매우 강한 화남"
    elif fear > 85:
        return 'high_fear', "매우 강한 두려움"

    # 4. 위의 조건을 모두 만족하지 못한 경우(안전 장치)
    # 마지막 fallback 리턴 수정
    return max(emotions, key=emotions.get), "가장 강한 감정 기반 추정"


# 해당 감정이 긍정, 부정, 중립인지 판별
def map_emotionType(emotion):
    # 긍정적인 감정 리스트
    positive_emotions = ["excited", "content", "curious", "amused", "affectionate", "high_happy", 
                         "middle_happy", "low_happy", "determined", "high_surprise", "middle_surprise", "low_surprise"]
    # 부정적인 감정 리스트
    negative_emotions = ["anxious", "nervous", "frustrated", "irritated", "melancholy", "tired", "bored", "shocked", "high_disgusted", 
                         "middle_disgusted", "low_disgusted", "high_sad", "middle_sad", "low_sad", "high_angry", "middle_angry", 
                         "low_angry", "high_fear", "middle_fear", "low_fear"]
    # 중립적인 감정 리스트
    neutral_emotions = ["high_neutral", "middle_neutral", "low_neutral", "confused", "serious"]

    # 감정값에 따라 리턴 값 설정
    if emotion in positive_emotions:
        return "positive", "긍정적인 감정"
    elif emotion in negative_emotions:
        return "negative", "부정적인 감정"
    elif emotion in neutral_emotions:
        return "neutral", "중립적인 감정"
    else:
        return "Unknown emotion", "Unknown detail"  # 만약 감정 값이 리스트에 없으면 Unknown으로 처리

# Stable Diffusion 이미지 생성 함수
def stable_diffusion_generate_image(fileName, filePath, prompt):

    model_id = "stabilityai/stable-diffusion-xl-base-1.0"

    # 4bit 양자화 설정
    dtype = torch.float16
    nf4_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_quant_type="nf4",
    bnb_4bit_compute_dtype=dtype
    )

    # ✅ 오류 방지를 위해 try-except 처리
    try:
        pipeline = StableDiffusionXLPipeline.from_pretrained(
            model_id,
            torch_dtype=dtype,
            quantization_config=nf4_config
        )
    except Exception as e:
        print("4bit 로딩 실패. float16으로 대체합니다:", e)
        pipeline = StableDiffusionXLPipeline.from_pretrained(
            model_id,
            torch_dtype=dtype
        )


    # 파이프라인 로드 (transformer 지정 불필요 — SDXL은 내부적으로 구성됨)
    pipeline = StableDiffusionXLPipeline.from_pretrained(
        model_id,
        torch_dtype=dtype,
        quantization_config=nf4_config
    )

    # 메모리 최적화 옵션
    pipeline.enable_model_cpu_offload()  # GPU 메모리 적을 때 필수
    pipeline.enable_attention_slicing()

    # 프롬프트 설정

    # 이미지 생성
    image = pipeline(
        prompt=prompt,
        height=1024,  # SDXL는 기본적으로 1024x1024 해상도에 최적화됨
        width=1024,
        num_inference_steps=40,  # 일반적으로 SDXL는 최소 25~30 스텝 추천
        guidance_scale=7.5
    ).images[0]

    # 사용 후 메모리 정리
    del pipeline
    torch.cuda.empty_cache()

    # PIL → NumPy (RGB) → BGR로 변환
    image_np = np.array(image)
    image_bgr = cv2.cvtColor(image_np, cv2.COLOR_RGB2BGR)

    # 저장할 이미지 경로와 이름 재설정
    num = random.randint(1, 100000)

    saved_imgPath = filePath.replace('client', 'generatedContents')
    saved_imgName = str(num) + "_AI_IMG_" + fileName


    # 3. PNG로 인코딩
    success, encoded_image = cv2.imencode(".png", image_bgr)
    if not success:
        raise ValueError("이미지를 인코딩할 수 없습니다.")

    binary_image_data = encoded_image.tobytes()

    # 4. GCS 업로드
    upload_result = upload_file_to_gcs(
        bucket_name="emosync-bucket",
        destination_blob_name=saved_imgPath + saved_imgName,
        binary_data=binary_image_data,
        content_type="image/png",
        img_or_music="img"
    )

    if upload_result == False:
        return "null", "null"
    
    return saved_imgName, saved_imgPath

    # 감정에 따른 이미지 생성 함수
def generate_image(user_fileName, user_filePath, emotion):

     #복합 감정
    if emotion == "excited":
        prompts_image = [
            "a peaceful lakeside at sunset, pastel skies, gentle ripples on the water, Ghibli-style",
            "a cozy cabin in a snow-covered forest, warm glowing lights and gentle snowfall, Ghibli-style",
            "a serene Zen garden with raked sand, bonsai trees, and stillness, Ghibli-style",
            "a countryside landscape with foggy hills, golden wheat fields and a small windmill, Ghibli-style",
            "a calm starry night sky viewed from a quiet mountaintop, glowing stars, Ghibli-style"
        ]
    elif emotion == "content":
        prompts_image = [
            "a sunlit room with plants and books, cozy interior with wooden textures, Ghibli-style",
            "a family picnic under a large oak tree in spring, with homemade food and laughter, Ghibli-style",
            "a cat napping beside a fireplace, warm amber tones and rustic textures, Ghibli-style",
            "a sunrise over a calm ocean, with gentle waves and a small boat, Ghibli-style",
            "a colorful blooming garden filled with butterflies and birds, Ghibli-style"
        ]
    elif emotion == "curious":
        prompts_image = prompt_image = [
            "a mysterious ancient library with floating glowing books, warm candlelight, Ghibli-style",
            "a hidden fairy village deep in the forest, with mushroom houses and tiny lanterns, Ghibli-style",
            "an astronaut exploring an alien jungle with fantastical creatures and glowing plants, Ghibli-style",
            "a child discovering a magical portal in the woods with shimmering light, Ghibli-style",
            "a treasure hunter in mossy ruins with golden beams of sunlight shining through cracks, Ghibli-style"
        ]
    elif emotion == "amused":
        prompts_image = [
            "cartoon animals having a tea party in a whimsical forest clearing, Ghibli-style",
            "a joyful dog chasing bubbles in a sunlit grassy field, Ghibli-style",
            "a colorful circus with playful animals, laughter and balloons, Ghibli-style",
            "a child splashing in a puddle under warm summer rain, Ghibli-style",
            "penguins dancing in snow with funny hats and scarves, Ghibli-style"
        ]
    elif emotion == "anxious":
        prompts_image = [
            "a glowing lantern in a dark forest, surrounded by mist and gentle snowfall, Ghibli-style",
            "a cozy bedroom with thick blankets and soft rain outside the window, Ghibli-style",
            "a cat curled up next to someone reading a book under dim light, Ghibli-style",
            "a serene hot spring in the mountains surrounded by fog and quiet, Ghibli-style",
            "a single lotus flower floating on a quiet moonlit pond, Ghibli-style"
        ]
    elif emotion == "nervous":
        prompts_image = [
            "a soft sunbeam illuminating a quiet room with old books and plants, Ghibli-style",
            "a person meditating on a mountain peak at dawn with clouds below, Ghibli-style",
            "a tea ceremony in a wooden house, soft steam and quietness, Ghibli-style",
            "a forest path covered in autumn leaves and calm light, Ghibli-style",
            "a hammock swaying under palm trees by the sea with birds chirping, Ghibli-style"
        ]
    elif emotion == "frustrated":
        prompts_image = [
            "a flower breaking through cracked concrete under sunrise light, Ghibli-style",
            "a glowing sunrise after a stormy night over hills, Ghibli-style",
            "a determined person climbing a steep mountain with light above, Ghibli-style",
            "a phoenix rising in golden flames from ashes, mystical and powerful, Ghibli-style",
            "a green sprout emerging from dry soil under gentle rain, Ghibli-style"
        ]
    elif emotion == "irritated":
        prompts_image = [
            "a calm koi pond with lily pads and gentle ripples, Ghibli-style",
            "rain softly falling on a quiet nighttime street with lamplight reflections, Ghibli-style",
            "a golden wheat field swaying in wind beneath blue skies, Ghibli-style",
            "a tranquil temple garden with incense smoke drifting in the air, Ghibli-style",
            "a sleepy fox in a cozy burrow with soft lighting, Ghibli-style"
        ]
    elif emotion == "melancholy":
        prompts_image = [
            "two hands holding gently under warm candlelight, Ghibli-style",
            "a golden-leaved tree standing alone on a hill, wind softly blowing, Ghibli-style",
            "a gentle sunrise breaking through morning fog in a quiet village, Ghibli-style",
            "a small bird singing on a windowsill with morning mist, Ghibli-style",
            "two people hugging under a shared umbrella in soft rain, Ghibli-style"
        ]
    elif emotion == "tired":
        prompts_image = [
            "a cozy bed with fluffy blankets and warm glowing light, Ghibli-style",
            "a spa with candles, soft towels, and tranquil water sounds, Ghibli-style",
            "a cat napping in a sunbeam on a wooden floor, Ghibli-style",
            "a hammock under gently swaying trees with birds above, Ghibli-style",
            "a steaming cup of tea on a windowsill as rain falls outside, Ghibli-style"
        ]
    elif emotion == "bored":
        prompts_image = [
            "an art studio with scattered paintings and glowing imagination, Ghibli-style",
            "a fantasy garden with glowing flowers and floating lights, Ghibli-style",
            "a portal glowing in a hidden forest grove, full of wonder, Ghibli-style",
            "a whimsical amusement park with twinkling lights and magical rides, Ghibli-style",
            "a dreamlike world with floating islands and waterfalls in the sky, Ghibli-style"
        ]
    elif emotion == "shocked":
        prompts_image = [
            "a soft sky with dramatic golden rays breaking through clouds, Ghibli-style",
            "a deer standing still in a misty forest clearing, peaceful and magical, Ghibli-style",
            "a mother gently holding a baby near a sunlit window, Ghibli-style",
            "a lighthouse beaming through a stormy sea, symbol of safety, Ghibli-style",
            "a cozy cabin interior glowing warmly, safe from chaos outside, Ghibli-style"
        ]
    elif emotion == "confused":
        prompts_image = [
            "a foggy forest path leading toward a mysterious light, Ghibli-style",
            "an ancient map unfolding on a rustic table with candles, Ghibli-style",
            "a wise owl on a tall tree under a starry night, Ghibli-style",
            "a glowing compass in a traveler's hand in twilight, Ghibli-style",
            "a river calmly flowing through a forest with magical sparkles, Ghibli-style"
        ]
    elif emotion == "affectionate":
        prompts_image = [
            "two people holding hands under a cherry blossom tree, petals falling, Ghibli-style",
            "a dog resting its head on someone’s lap with warm afternoon light, Ghibli-style",
            "a couple laughing together in a sunlit field full of wildflowers, Ghibli-style",
            "a child gently petting a cat on a wooden porch, Ghibli-style",
            "a warm hug between two friends under fairy lights at dusk, Ghibli-style"
        ]
    elif emotion == "serious":
        prompts_image = [
            "a lone thinker reading in a vast, ancient library, Ghibli-style",
            "a monk meditating on a mountaintop at sunset with clouds swirling, Ghibli-style",
            "a candle-lit study room with parchment, books, and stillness, Ghibli-style",
            "a chessboard mid-game in a quiet wooden cabin, deep focus, Ghibli-style",
            "a dark enchanted forest path lit by soft glowing orbs, Ghibli-style"
        ]
    elif emotion == "determined":
        prompts_image = [
            "a warrior climbing a snowy peak with wind and determination, Ghibli-style",
            "a road stretching endlessly through a glowing desert, Ghibli-style",
            "a lone tree standing tall in a thunderstorm, never breaking, Ghibli-style",
            "a person running toward the sunrise with glowing energy, Ghibli-style",
            "a lighthouse shining brightly through thick fog, Ghibli-style"
        ]

    
    # 단일 감정
    # sad
    elif emotion == "high_sad":
        prompts_image = ["A lonely child being comforted by a glowing forest spirit, soft rain falling, Ghibli-style","A warm light shining through a window onto a tearful face, comforted by a cat, Ghibli-style","A small figure sitting under a giant tree glowing with magical light, calm and healing, Ghibli-style","A child hugging a gentle mythical creature in a quiet twilight forest, Ghibli-style","An old house glowing from within on a cold rainy night, a place of emotional refuge, Ghibli-style."]
    elif emotion == "middle_sad":
        prompts_image = ["A person watching the sunset on a quiet hill, wind softly blowing, Ghibli-style","A small boat drifting peacefully on a moonlit lake, stars twinkling above, Ghibli-style","Two friends sitting silently under a sakura tree, petals falling, Ghibli-style","A magical creature gently offering a flower to a sad child, pastel tones, Ghibli-style","A calm night sky with shooting stars above a quiet village, Ghibli-style."]
    elif emotion == "low_sad":
        prompts_image = ["A cozy tea shop glowing warmly during dusk, welcoming aura, Ghibli-style","A field of soft flowers swaying in the breeze, gentle sun, Ghibli-style","A small creature dancing in the rain, joyful and silly, Ghibli-style","A quiet library filled with light and plants, comforting solitude, Ghibli-style","A child painting under the sky with friends nearby, colorful, light-hearted, Ghibli-style."]

    # angry
    elif emotion == "high_angry":
        prompts_image = [
        "Ghibli-style warrior sitting calmly in a fiery battlefield, light breaking through the smoke",
        "A brave girl taming a red dragon surrounded by lava, symbolizing inner strength,Ghibli-style",
        "A tree growing strong amidst a volcanic eruption, glowing with resilience,Ghibli-style",
        "A character releasing anger into glowing lanterns that float peacefully into the night sky,Ghibli-style",
        "A storm clearing above a mountain temple, with golden rays shining through—symbol of emotional calm,Ghibli-style."
    ]
    elif emotion == "middle_angry":
        prompts_image = [
            "A Ghibli-inspired forest where dark clouds part to reveal a gentle sunrise",
            "A young traveler crossing a broken bridge, rebuilding it with glowing stones of peace,Ghibli-style",
            "A phoenix rising from ashes with golden feathers, symbolizing transformation,Ghibli-style",
            "A soft rain falling in a village where everyone holds hands, warming each other,Ghibli-style",
            "An ancient guardian beast resting beside a calm river after a long journey,Ghibli-style."
    ]
    elif emotion == "low_angry":
        prompts_image = [
            "A quiet, rainy Ghibli town with warm yellow lights in every window, evoking safety,Ghibli-style",
            "A child holding a glowing flower in a thunderstorm, symbol of hope in anger,Ghibli-style",
            "A fox curled up in a mossy shrine during a light drizzle, eyes slowly closing in rest,Ghibli-style",
            "A peaceful tea garden after a storm, with fallen leaves scattered on stone paths,Ghibli-style",
            "A soft breeze in the mountains brushing through tall grass, inviting calm reflection,Ghibli-style."
    ]
    # happy
    elif emotion == "high_happy":
        prompts_image = [
            "A Ghibli-style flower festival with dancing characters and magical fireworks",
            "A group of friends riding on a giant bird above glowing fields",
            "A picnic under giant sunflowers with floating food trays and laughing spirits",
            "A cottage on a floating island, surrounded by glowing clouds and joy",
            "A field of glowing dandelions blowing in the wind as kids play and chase them."
    ]
    elif emotion == "middle_happy":
        prompts_image = [
            "A cozy village morning with everyone greeting each other warmly",
            "A sunbeam lighting a breakfast table with warm pastries and pets",
            "A bike ride through flower fields with music following behind magically",
            "Children running with kites shaped like dragons in a Ghibli hilltop village",
            "A magical bakery where sweets float gently into the hands of smiling customers."
    ]
    elif emotion == "low_happy":
        prompts_image = [
            "A peaceful dock with boats swaying gently in the golden sunrise",
            "A small garden blooming gently as the sun rises behind a mountain",
            "A sleepy animal waking up in a sunbeam, stretching happily",
            "A young girl writing in her journal with a soft smile, surrounded by blooming pots",
            "A gentle breeze making chimes sing as a cat naps on a porch swing."
    ]

    # neutral
    elif emotion == "high_neutral":
        prompts_image = [
            "A Ghibli meadow with butterflies, flowing rivers, and distant singing birds",
            "A young character peacefully painting in a field surrounded by floating spirits",
            "A crystal-clear lake reflecting mountains under a sunny blue sky",
            "A calm forest with sunbeams dancing through the trees and little spirits watching",
            "A Ghibli-style observatory on a quiet hill, where a character watches the stars rise."
    ]
    elif emotion == "middle_neutral":
        prompts_image = [
            "A fox napping in a mossy cave, with fireflies lighting the entrance",
            "A little hut by a rice field with smoke rising from the chimney and birds flying",
            "A quiet library hidden in the forest, with books floating softly around",
            "A stone path winding through a peaceful bamboo forest",
            "A character walking a familiar trail with wind chimes ringing softly."
    ]
    elif emotion == "low_neutral":
        prompts_image = [
            "A wooden bridge over a calm river, with reflections of clouds passing slowly",
            "A small shrine in the forest surrounded by falling leaves",
            "A lantern-lit path guiding a traveler in the twilight",
            "A soft rain falling on lily pads in a peaceful pond",
            "A sleepy town square at dusk, with lanterns gently swaying in the wind."
    ]

    # surprise
    elif emotion == "high_surprise":
        prompts_image = [
            "A sudden burst of warm sunlight breaking gently through dark clouds over a quiet Ghibli-style village, with soft glowing particles floating in the air",
            "A Ghibli-style forest clearing where sparkling fireflies dance around a small, glowing pond under a brightening sky",
            "A gentle breeze sweeping through a field of tall grass in a Ghibli meadow, with floating petals and curious woodland creatures",
            "A quiet Ghibli village waking to a golden sunrise, with soft pastel clouds and birds soaring joyfully overhead",
            "A magical tree opening its blossoms slowly in Ghibli style, revealing glowing spirits that calm the heart."
    ]
    elif emotion == "middle_surprise":
        prompts_image = [
            "A whimsical Ghibli-inspired forest path with mysterious twists and turns, where gentle, bright colors and playful spirits appear unexpectedly",
            "A Ghibli-style mountain trail bathed in soft morning light, where curious animals peek from behind rocks and trees",
            "A cozy Ghibli tea house nestled in a vibrant garden, with warm light spilling through paper windows as spirits gather quietly",
            "A floating lantern festival in a Ghibli village, with lanterns slowly rising to the soft colors of twilight",
            "A Ghibli-style hidden glade filled with glowing mushrooms and soft mist, inviting peaceful discovery."
    ]
    elif emotion == "low_surprise":
        prompts_image = [
            "A playful Ghibli-style scene with unusual, rounded shapes and soft pastel colors, where curious creatures explore an enchanted garden",
            "A quiet Ghibli village square bathed in soft afternoon light, with children laughing and chasing butterflies",
            "A Ghibli-style gentle rain falling over a serene pond, where koi fish swim slowly beneath floating leaves",
            "A cozy nook inside a Ghibli treehouse, with sunlight filtering softly through stained glass",
            "A small Ghibli windmill turning slowly in a gentle breeze, surrounded by colorful flowers."
    ]

    # fear
    elif emotion == "high_fear":
        prompts_image = [
            "A dark forest in Ghibli-style where twisted trees stand tall but gentle glowing lights and friendly forest spirits bring warmth and calm to the misty night",
            "A Ghibli-style ancient shrine glowing softly under a moonlit sky, with comforting spirits guarding the path",
            "A quiet, cozy cottage surrounded by softly glowing fireflies in a shadowy Ghibli forest",
            "A mystical Ghibli river flowing calmly through a dark forest, reflecting stars and gentle spirits",
            "A small Ghibli village where lanterns float in the night sky, symbolizing hope and protection."
    ]
    elif emotion == "middle_fear":
        prompts_image = [
            "A quiet Ghibli-inspired alley softly lit by lanterns, where shadows slowly fade and kind faces emerge, easing feelings of unease",
            "A Ghibli-style bridge over a foggy river, where glowing spirits guide travelers safely across",
            "A peaceful Ghibli garden filled with blooming flowers and softly chirping birds after a stormy night",
            "A gentle Ghibli rain washing over a village, cleansing and renewing the land and hearts",
            "A softly glowing Ghibli lantern hanging on an old wooden gate, welcoming wanderers with warmth."
    ]
    elif emotion == "low_fear":
        prompts_image = [
            "A foggy Ghibli-style landscape with soft silhouettes of trees and gentle glowing orbs, creating a dreamy atmosphere of safety and peace",
            "A quiet meadow in Ghibli style, where friendly animals graze under the soft light of dawn",
            "A Ghibli-style stone path winding through a gentle forest with warm sunlight breaking through the mist",
            "A small Ghibli wooden bridge over a clear stream, surrounded by blooming flowers and calm nature sounds",
            "A soft breeze rustling through a Ghibli field of tall grass, inviting calm and relaxation."
    ]

    # disgust
    elif emotion == "high_disgust":
        prompts_image = [
            "A decaying old garden in Ghibli-style where moss and flowers slowly reclaim forgotten stone statues, transforming decay into quiet beauty",
            "A Ghibli-style swamp where soft glowing plants grow, lighting the darkness with gentle, magical hues",
            "An abandoned Ghibli-style village slowly embraced by nature, with vines and blossoms covering old buildings",
            "A mystical Ghibli forest floor where fallen leaves rot but give rise to new, colorful mushrooms and tiny creatures",
            "A forgotten Ghibli shrine overgrown with moss, bathed in soft afternoon light, evoking peaceful renewal."
    ]
    elif emotion == "middle_disgust":
        prompts_image = [
            "An abstract Ghibli-inspired scene of tangled roots and twisted vines, softened by gentle light and delicate butterflies bringing life to the darkness",
            "A Ghibli-style mossy cave with glowing crystals and softly glowing insects illuminating the shadows",
            "A gentle rain in a Ghibli forest washing away dirt and grime, leaving leaves fresh and glistening",
            "A calm Ghibli stream flowing through tangled roots, clear and sparkling under dappled sunlight",
            "A Ghibli-style village market where fresh flowers and fruits brighten every corner, dispelling gloom."
    ]
    elif emotion == "low_disgust":
        prompts_image = [
            "A muted Ghibli-style room with worn wooden furniture and soft, warm sunlight filtering through stained glass windows, evoking calm and comfort",
            "A quiet Ghibli tea garden where soft moss carpets the ground and gentle breezes carry floral scents",
            "A Ghibli-style forest clearing with soft golden light filtering through ancient trees, inviting rest and peace",
            "A cozy corner in a Ghibli house with handmade pottery and gently glowing candles",
            "A small Ghibli library filled with well-worn books and the scent of old paper, offering quiet refuge."
    ]
    prompt_image = random.choice(prompts_image)
    # Stable Diffusion 이미지 생성
    print(f"이미지 생성 프롬프트: {prompt_image}")
    saved_imgName, saved_imgPath = stable_diffusion_generate_image(user_fileName, user_filePath, prompt_image)  # Stable Diffusion 이미지 생성 함수
    return saved_imgName, saved_imgPath

# 음악 생성 함수
def generate_music(fileName, filePath, emotion):
    # 모델 로드
    MODEL_NAME = "facebook/musicgen-small"
    device = "cuda" if torch.cuda.is_available() else "cpu"
    
    model = MusicgenForConditionalGeneration.from_pretrained(MODEL_NAME).to(device)
    processor = AutoProcessor.from_pretrained(MODEL_NAME)

    #sad
    if emotion == "high_sad":
            prompts_music = ["An emotional and uplifting symphony, transitioning from sadness to hope", "A powerful ballad with emotional piano and soaring vocals"]
    elif emotion == "middle_sad":
            prompts_music = ["A cinematic emotional piece with slow string swells and soft piano", "A hopeful folk melody with bright guitar and warm harmonies"]
    elif emotion == "low_sad":
            prompts_music = ["Soft piano melody with light violin, evoking comfort and warmth", "A gentle acoustic guitar tune with a nostalgic yet soothing tone"]
        
    # angry
    elif emotion == "high_angry":
        prompts_music = ["A powerful orchestral piece with intense drums and dramatic strings", "A high-energy rock anthem with electric guitar riffs"]
    elif emotion == "middle_angry":
        prompts_music = ["A melodic rock ballad with emotional guitar solos", "An uplifting cinematic soundtrack with a gradual rise"]
    elif emotion == "low_angry":
        prompts_music = ["A deep and slow piano melody with calming orchestral strings", "Soft lo-fi beats with mellow guitar and raindrop sounds"]

    # happy
    elif emotion == "high_happy":
        prompts_music = ["An energetic dance track with driving beats and uplifting synth melodies", "A fast-paced orchestral piece with soaring strings"]
    elif emotion == "middle_happy":
        prompts_music = ["An upbeat indie pop track with clapping percussion and catchy melodies", "A groovy jazz piece with lively piano and energetic brass"]
    elif emotion == "low_happy":
        prompts_music = ["A gentle folk tune with acoustic guitar and soft humming", "A soothing instrumental with piano and bells"]

    # neutral
    elif emotion == "high_neutral":
        prompts_music = ["A bright and energetic pop instrumental with cheerful melodies", "A funky jazz fusion track with groovy bass"]
    elif emotion == "middle_neutral":
        prompts_music = ["A smooth lounge jazz with uplifting saxophone", "A vibrant yet calming world music piece with exotic percussion"]
    elif emotion == "low_neutral":
        prompts_music = ["A soft lo-fi chillhop beat with warm vinyl crackle", "A gentle jazz piece with soft piano and light brushes on drums"]

    # surprise
    elif emotion == "high_surprise":
        prompts_music = ["An epic adventure soundtrack with bold brass and dramatic percussion", "A magical symphony filled with whimsical instruments"]
    elif emotion == "middle_surprise":
        prompts_music = ["A cinematic theme with uplifting string swells", "A dynamic jazz improvisation with playful saxophone"]
    elif emotion == "low_surprise":
        prompts_music = ["A soft and playful piano melody", "A gentle orchestral piece with light pizzicato strings"]

    # fear
    elif emotion == "high_fear":
        prompts_music = ["A heroic orchestral score with powerful drums", "Epic fantasy music with mystical strings"]
    elif emotion == "middle_fear":
        prompts_music = ["A cinematic, hopeful piano melody", "A serene electronic tune with pulsing synths"]
    elif emotion == "low_fear":
        prompts_music = ["A warm and comforting lullaby", "Gentle acoustic guitar with a steady rhythm"]

    # disgust
    elif emotion == "high_disgust":
        prompts_music = ["Powerful cinematic music with a rising orchestral swell", "Deep atmospheric electronic music with evolving synth layers"]
    elif emotion == "middle_disgust":
        prompts_music = ["Melodic orchestral strings with a sense of cleansing", "Soft electronic pads and light percussion"]
    elif emotion == "low_disgust":
        prompts_music = ["Soft acoustic guitar melody with gentle piano", "Calm and soothing ambient music with light wind chimes"]

         #복합 감정
    elif emotion == "excited":
        prompts_music = [
            "a peaceful lakeside at sunset, soft pastel colors, gentle ripples on the water",
            "a cozy cabin in a snow-covered forest, warm light glowing from the windows",
            "a serene Zen garden with raked sand and bonsai trees, minimalism style",
            "a quiet countryside landscape with foggy hills and golden fields",
            "a calm night sky filled with stars, viewed from a quiet mountaintop"
        ]
        

    elif emotion == "content":
        prompts_music = [
            "a bright sunlit room with plants and books, cozy and minimalist design",
            "a warm family picnic under a big oak tree in spring",
            "a cat sleeping peacefully by a fireplace, warm tones",
            "a soft sunrise over the ocean, tranquil and expansive",
            "a blooming garden with butterflies and gentle breeze"
        ]
        

    elif emotion == "curious":
        prompts_music = [
            "a mysterious ancient library filled with glowing books and secrets",
            "a hidden fairy village in a forest, whimsical and enchanting",
            "an astronaut exploring an alien jungle full of strange creatures",
            "a small child discovering a magical portal in the woods",
            "a treasure hunter exploring ancient ruins with golden light shining through cracks"
        ]
       

    elif emotion == "amused":
        prompts_music = [
            "a group of cartoon animals having a tea party in the forest",
            "a joyful dog chasing bubbles in a sunlit park",
            "a colorful circus scene with clowns and animals playing together",
            "a happy child splashing in a puddle during a summer rain",
            "a bunch of penguins dancing in a snowy landscape with funny hats"
        ]
       

    elif emotion == "anxious":
        prompts_music = [
            "a soft glowing lantern in the dark forest, safe and comforting",
            "a cozy bedroom with blankets and rain softly falling outside",
            "a cat curled up next to a sleepy person reading a book",
            "a peaceful hot spring in the mountains surrounded by mist",
            "a floating lotus on still water under moonlight"
        ]
       
    elif emotion == "nervous":
        prompts_music = [
            "a warm sunbeam coming through a window into a quiet room",
            "a person meditating at dawn on a quiet mountain peak",
            "a cozy tea ceremony scene with warm tones and calm atmosphere",
            "a soft forest path in autumn with falling leaves",
            "a relaxing hammock under palm trees by the sea"
        ]
       

    elif emotion == "frustrated":
        prompts_music = [
            "a flower blooming through cracked concrete, symbol of resilience",
            "a sunrise after a storm, new beginnings and hope",
            "a person climbing a steep mountain with determination and light above",
            "a phoenix rising from ashes in a golden burst of light",
            "a green sprout growing from dry earth, symbol of new life"
        ]
       

    elif emotion == "irritated":
        prompts_music = [
            "a peaceful koi pond with lilies and calm reflections",
            "a soft rain falling on a quiet city street at night",
            "a gentle breeze moving through a wheat field under a blue sky",
            "a serene temple garden with incense smoke curling through the air",
            "a sleepy fox resting in a cozy burrow, warm tones"
        ]
       

    elif emotion == "melancholy":
        prompts_music = [
            "a warm hand holding another under soft candlelight",
            "a tree with golden leaves standing alone, serene and beautiful",
            "a gentle sunrise breaking through fog, bringing light",
            "a small bird singing on a windowsill during a grey morning",
            "a quiet hug between two people under an umbrella"
        ]
        

    elif emotion == "tired":
        prompts_music = [
            "a cozy bed with soft blankets and warm lighting",
            "a quiet spa with candles and calm water sounds",
            "a cat sleeping in a sunbeam on a wooden floor",
            "a soft hammock under trees swaying gently in the breeze",
            "a steaming cup of tea on a windowsill with rain outside"
        ]
        
    elif emotion == "bored":
        prompts_music = [
            "a vibrant art studio with scattered paintings and tools",
            "a magical garden with glowing plants and fantasy elements",
            "a person entering a glowing portal to another world",
            "a whimsical amusement park with colorful lights and rides",
            "a surreal dreamscape with floating islands and waterfalls"
        ]
        

    elif emotion == "shocked":
        prompts_music = [
            "a soft cloudy sky with golden light breaking through",
            "a peaceful deer standing in a misty forest clearing",
            "a mother holding a baby gently in a sunlit room",
            "a lighthouse shining through a stormy sea, symbol of guidance",
            "a warm cabin interior, safe and quiet from the chaos outside"
        ]
       

    elif emotion == "confused":
        prompts_music = [
            "a path through a foggy forest leading to light",
            "a map unfolding to reveal a clear destination",
            "a wise owl sitting on a tree under a starry sky",
            "a compass glowing softly in a traveler’s hand",
            "a calm river flowing steadily through rocks and trees"
        ]
        

    elif emotion == "affectionate":
        prompts_music = [
            "two people holding hands under a tree with falling petals",
            "a dog resting its head on a person’s lap, cozy and soft",
            "a couple laughing together in a sunlit field",
            "a cat rubbing its head against a child’s hand",
            "a warm hug between two characters under fairy lights"
        ]
        
    elif emotion == "serious":
        prompts_music = [
            "a lone thinker in a vast library filled with ancient books",
            "a monk meditating on a high mountain under a clear sky",
            "a candle-lit study room with parchment and ink",
            "a chessboard mid-game in a dimly lit room",
            "a dark forest with light guiding the way forward"
        ]
       
    elif emotion == "determined":
        prompts_music = [
            "a warrior climbing a snowy peak, sword in hand, wind blowing",
            "a long road through a desert with mountains on the horizon",
            "a single tree standing tall in a storm",
            "a person running towards the sunrise, never looking back",
            "a lighthouse shining bright through heavy fog, unwavering"
        ]
       


    
    prompt_music = random.choice(prompts_music)
    print(f"음악 생성 프롬프트: {prompt_music}")
    
    # MusicGen 음악 생성
    inputs = processor(text=prompt_music, padding=True, return_tensors="pt").to(device)
    audio_values = model.generate(**inputs, max_new_tokens=1024)
    
    # 샘플링 속도 확인 후 조정
    sampling_rate = 32000  # MusicGen의 기본 샘플링 속도
    audio_data = audio_values[0].cpu().numpy()

    # 데이터 정규화 및 저장
    audio_data = np.clip(audio_data, -1, 1)
    audio_data = np.int16(audio_data * 32767)
    audio_data = audio_data.reshape(-1, 1)


     # 저장할 음악 경로와 이름 재설정
    num = random.randint(1, 100000)
    saved_musicPath = filePath.replace('client', 'generatedContents')
    saved_musicPath = saved_musicPath.replace('audio', 'music')
    saved_musicName = fileName.split('.')[0]  # 파일 확장자 제거
    saved_musicName = str(num) + "_AI_MUSIC_" + saved_musicName + ".mp3"

    print(f"****음악 저장 경로 확인:{saved_musicPath}")

    # 1. NumPy 배열을 WAV로 메모리 버퍼에 저장
    wav_io = io.BytesIO()
    sf.write(wav_io, audio_data, samplerate=sampling_rate, format='WAV')
    wav_io.seek(0)

    # 2. WAV 데이터를 AudioSegment로 불러오기
    audio = AudioSegment.from_file(wav_io, format="wav")

    # 3. MP3로 변환하여 BytesIO에 저장
    audio_io = io.BytesIO()
    audio.export(audio_io, format="mp3")
    audio_io.seek(0)

    # 4. GCS에 업로드
    upload_result = upload_file_to_gcs(
        bucket_name="emosync-bucket",
        destination_blob_name=saved_musicPath + saved_musicName,
        binary_data=audio_io,
        content_type="audio/mpeg",
        img_or_music="music"
    )

    if upload_result == False:
        return "null", "null"

    #scipy.io.wavfile.write(saved_musicPath + saved_musicName, rate=sampling_rate, data=audio_data)
    print("음악 생성 완료! 'generated_music.wav' 파일을 확인하세요.")
    return saved_musicName, saved_musicPath


# GCS 에 파일 업로드
def upload_file_to_gcs(bucket_name, destination_blob_name, binary_data, content_type, img_or_music):

    try:
        # JSON 키 파일 경로
        BASE_DIR = os.path.dirname(os.path.abspath(__file__))
        key_path = os.path.join(BASE_DIR, "astute-strategy-458803-n8-d140f4222a6d.json")

        # 서비스 계정 인증 객체 생성
        credentials = service_account.Credentials.from_service_account_file(key_path)

        # 클라이언트 생성 시 credentials 직접 전달
        client = storage.Client(credentials=credentials, project=credentials.project_id)

        bucket = client.bucket(bucket_name)
        blob = bucket.blob(destination_blob_name)

        # 메모리 내 binary 데이터를 업로드
        if(img_or_music == "img"):
            blob.upload_from_file(io.BytesIO(binary_data), content_type=content_type)
        elif(img_or_music == "music"):
            blob.upload_from_file(binary_data, content_type=content_type)

        print(f"{destination_blob_name} 파일이 GCS에 업로드되었습니다.")
        return True
    
    except Exception as e:
        print(f"예상치 못한 오류 발생: {e}")
        return False
