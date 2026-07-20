# 팀플 AI 매니저 (Teample AI Manager)

@AGENTS.md

## 구조
모노레포: frontend/ (Next.js) + backend/ (Spring Boot 3, Java 17)
- 프론트 → Vercel, 백엔드 → Railway, DB → Supabase PostgreSQL
- 프론트는 NEXT_PUBLIC_API_URL로 백엔드 호출. DB 직접 접근 금지
- 타입: 백엔드 springdoc-openapi → frontend에서 npm run gen:types

## MVP 1차 목표
회의록 자동 생성 하나만: 카톡 붙여넣기 → Claude API → 회의록 JSON
→ 수정 가능한 편집 UI. AI 결과는 항상 사람이 수정/확정.

## 프론트 규칙 (frontend/)
- Next.js 15 App Router, TypeScript strict, Tailwind + shadcn/ui
- params는 Promise: const { id } = await params
- 컴포넌트: src/features/<기능>/components/ 구조
- FE-1(본인): 입력 페이지/폼 담당, FE-2: 결과/편집 UI 담당

## 백엔드 규칙 (backend/)
- Controller/Service/Repository 계층, DTO로 응답
- Claude API는 anthropic-java SDK, 키는 환경변수
- CORS: localhost:3000 + *.vercel.app

## 협업
- main 직접 푸시 금지, 기능별 브랜치(feat/...)
- 커밋 자주, 기능 단위 PR
