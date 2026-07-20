# 팀플 AI 매니저

카톡 대화를 붙여넣으면 AI가 회의록을 자동 생성하는 웹서비스.

## 구조

```
teample-ai-manager/
├── frontend/    Next.js (TypeScript) → Vercel
└── backend/     Spring Boot 3 (Java 17) → Railway
```

## 실행

```bash
# Frontend
cd frontend && npm install && npm run dev    # localhost:3000

# Backend
cd backend && ./gradlew bootRun              # localhost:8080
```

## 환경변수

- Frontend: `frontend/.env.local.example` 참고
- Backend: `backend/src/main/resources/application.yml`에 환경변수로 주입 (하드코딩 금지)
