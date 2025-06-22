from flask import Flask, request, jsonify
from pyngrok import ngrok
from generate04 import func_deepface, func_SER, emotion_integration, generate_image, generate_music, map_emotion, map_emotionType

# 고정 도메인 연결
public_url = ngrok.connect(addr=5000, proto="http", hostname="gently-artistic-magpie.ngrok-free.app")
print(" * Public URL:", public_url)

app = Flask(__name__)

@app.route("/")
def hello():
    return "Hello Python!"

@app.route('/generatedContents', methods=['POST'])
def apiTest():
    print('apiTest IN')
    param = request.get_json()

    #-----------------------
    # GCS 공통 경로
    FileRoot = param.get("filePath_root")

    # 사용자의 얼굴사진 이름
    img_reFileName = param.get("img_reFileName")

    # 사용자의 음성 파일 이름
    audio_reFileName = param.get("audio_reFileName")

    # 얼굴 사진 경로
    img_relative_path = param.get("img_relative_path")

    # 음성 파일 경로
    audio_relative_path = param.get("audio_relative_path")
    #-----------------------
    
    generate_opt = param.get("generate_opt")
    FirstOrRetry = param.get("FirstOrRetry")
    # 재생성시 사용할 감정값
    saved_emotion = param.get("saved_emotion")

    # 초기 생성인 경우
    if(FirstOrRetry == "first"):

            # deepface로 감정 받아오기
            deepface_result = func_deepface(FileRoot, img_reFileName, img_relative_path)

            # SER로 감정 받아오기
            SER_restult = func_SER(FileRoot, audio_relative_path, audio_reFileName)

            # deepface, SER의 통합된 결과 받아오기
            integration_result = emotion_integration(deepface_result, SER_restult)

            # 통합 결과로 감정과 감정 설명 값 받아오기
            emotion, emotion_detail = map_emotion(integration_result)
            emotionType, emotionType_detail = map_emotionType(emotion)

            print(f"******감정 결과: {emotion}, 감정 설명: {emotion_detail}")
            print(f"******감정 타입: {emotionType}, 감정 타입 설명: {emotionType_detail}")
    

            # 얼굴 인식이 불가능한 경우
            if(emotion == "none"):
                result_data = {"emotion" : "No face detected", "emotionType" : "none", 
                        "emotion_detail" : "none", "emotionType_detail" : "none",
                        "generatedImageName" : "none", "generatedImagePath" : "none", 
                        "generatedMusicName" : "none", "generatedMusicPath" : "none"}
                
            # 정상적으로 작동한 경우
            else:
                saved_imgName, saved_imgPath = generate_image(img_reFileName, img_relative_path, emotion)
                saved_musicName, saved_musicPath = generate_music(audio_reFileName, audio_relative_path, emotion)
                result_data = { "emotion" : emotion, "emotionType" : emotionType, 
                        "emotion_detail" : emotion_detail, "emotionType_detail" : emotionType_detail,
                        "generatedImageName" : saved_imgName, "generatedImagePath" : saved_imgPath, 
                        "generatedMusicName" : saved_musicName, "generatedMusicPath" : saved_musicPath}
            

    # 재생성인 경우
    elif(FirstOrRetry == "retry"):

        # 생성 옵션이 이미지인 경우
        if (generate_opt == "image"):
            # 이미치 처리
            saved_imgName, saved_imgPath = generate_image(img_reFileName, img_relative_path, saved_emotion)
            result_data = { "generatedImageName" : saved_imgName, "generatedImagePath" : saved_imgPath}

        # 생성 옵션이 음악인 경우
        elif (generate_opt == "music"):
            # 음악 처리
            saved_musicName, saved_musicPath = generate_music(audio_reFileName, audio_relative_path, saved_emotion)
            result_data = {"generatedMusicName" : saved_musicName, "generatedMusicPath" : saved_musicPath}

        # 이미지, 음악 모두 생성하는 경우
        elif (generate_opt == "all"):
            saved_imgName, saved_imgPath = generate_image(img_reFileName, img_relative_path, saved_emotion)
            saved_musicName, saved_musicPath = generate_music(audio_reFileName, audio_relative_path, saved_emotion)
            result_data = { "generatedImageName" : saved_imgName, "generatedImagePath" : saved_imgPath, "generatedMusicName" : saved_musicName, "generatedMusicPath" : saved_musicPath}

    
    print('apiTest OUT')

    print('result: ', result_data)
    return jsonify(result_data)


if __name__ == "__main__":
    app.run(port=5000)