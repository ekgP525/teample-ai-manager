# 팀플 AI 매니저

카톡 대화를 붙여넣으면 AI가 회의록을 자동 생성하는 웹서비스.

## 구조

```
teample-ai-manager/
├── frontend/    Next.js (TypeScript) → Vercel
└── backend/     Spring Boot 4 (Java 21) → Railway
```

## 실행

```bash
# Frontend
cd frontend && npm ci && npm run dev    # localhost:3000

# Backend
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
```

## 환경변수

- Frontend: `frontend/.env.local.example` 참고
- Backend: `backend/src/main/resources/application.yml`에 환경변수로 주입 (하드코딩 금지)

프런트엔드는 실행 전에 `.env.local.example`을 `.env.local`로 복사하고 값을 확인합니다.
백엔드는 `.env.example`을 `.env`로 복사합니다. 로컬 `dev` 프로필은 DB를 지정하지 않으면
`backend/data/`의 파일 H2를 사용합니다. Windows에서는 `./gradlew` 대신 `./gradlew.bat`을 사용합니다.

기본 프로필은 운영용 `prod`입니다. 운영 DB·인증·AI·CORS 설정이 빠지면 기동을 중단합니다.
운영에는 `dev` 프로필을 사용하지 마세요. 자세한 설정과 기존 데이터 전환 절차는
[출시 가이드](docs/RELEASE.md)를 참조하세요.
