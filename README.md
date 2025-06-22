# 2025-1_Team.EmoSync
<div align= "center"> 
    <h2 style="border-bottom: 1px solid #21262d; color: #c9d1d9;"> 2025년 1학기 캡스톤디자인(감정 맞춤형 콘텐츠 생성기/ 류지호, 손원후, 조재영) </h2>  
</div>
<br>

<h3><li>개발 환경</li></h3><br>
<p>1. AI 모델</p>

| 이름 | 역할 |
| --- | --- |
| MusicGen | 음악 생성 |
| Stable Diffusion | 이미지 생성 |
| Whisper SER | 음성 기반 감정 분석 |
| Deepface | 표정 기반 감정 분석 |

<br><p>2. 데이터베이스</p>

| 분류 | 이름 |
| --- | --- |
| 데이터베이스 | MariaDB 11.5 |
| 데이터베이스 관리 도구 | HeidiSQL, IntelliJ IDEA Ultimate |
| JPA | Spring Data JPA |
| 클라우드 저장소 | Google Cloud Storage |

 <br><p>3. 웹 환경</p>

| 분류 | 이름 |
| --- | --- |
| 개발 도구 | IntelliJ IDEA 2024.2.1. |
| 언어 |  java 17.0.11 |
| 프레임워크 | Spring Boot 3.1.0 |
| 템플릿 엔진 | Thymeleaf |   
| 뷰 | Html5, Css3, Javascript, Bootstrap |  

 <br><p>4. 배포</p>

| 분류 | 이름 |
| --- | --- |
| 웹, 데이터베이스 | Cloudtype |
| 파이썬 | Ngrok | 
    
<br><h3><li>프로젝트 설명</li></h3>
<h4>본 프로젝트는 사용자의 표정과 음성을 입력받아 감정을 분석하고 그에 어울리는 이미지와 음악을 생성해줍니다.</h4>
<br>
<h5>프로젝트 흐름</h5>
<p>- AI 모델을 처리하는 파이썬 영역과 사용자가 접근할 수 있는 인터페이스를 제공해주는 웹 영역으로 구성되어있습니다</p>
<p>- 사용자는 웹 사이트에 접속하여 콘텐츠 생성을 요청하고 웹에서는 해당 요청을 받아 파이썬으로 전달합니다</p>
<p>- 파이썬에서는 AI모델을 이용하여 콘텐츠를 생성하고 결과 값을 웹으로 반환합니다</p>
<p>- 웹은 결과를 사용자에게 보여줍니다</p>
<br>
<h5>프로젝트 기능</h5>
<p>- 사용자의 입력값을 기반으로 콘텐츠 생성</p>
<p>- 과거에 생성한 콘텐츠 기록 열람</p>
<p>- 분석한 감정 기반으로 시각화된 통계 자료 제공</p>
<p>- 고객 문의 게시판 제공</p>
