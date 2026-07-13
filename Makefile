.PHONY: test backend-test frontend-test config-test build aot native-test compose-config

test: backend-test frontend-test config-test

backend-test:
	cd backend && ./mvnw test

frontend/node_modules/.package-lock.json: frontend/package.json frontend/package-lock.json
	cd frontend && npm ci

frontend-test: frontend/node_modules/.package-lock.json
	cd frontend && npm test && npm run typecheck

config-test:
	bash scripts/config-contract-smoke.sh

build: frontend/node_modules/.package-lock.json
	cd backend && ./mvnw -DskipTests package
	cd frontend && npm run build

aot:
	cd backend && ./mvnw -Pnative spring-boot:process-aot

native-test:
	cd backend && ./mvnw -PnativeTest test

compose-config:
	docker compose --env-file .env.example -f deploy/compose.yml config --quiet
