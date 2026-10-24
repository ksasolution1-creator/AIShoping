# 쇼핑몰(web)·관리자(admin) 공용 이미지. 빌드: docker build -f deploy/next.Dockerfile --build-arg APP=web .
# ARM(Oracle Ampere)·x86 모두 동작한다.
ARG NODE_VERSION=22-alpine

FROM node:${NODE_VERSION} AS build
ARG APP
# next.config의 rewrites는 빌드 시점에 고정되므로 컨테이너 안 API 주소를 여기서 넘긴다
ARG API_URL=http://api:18080
ARG NEXT_PUBLIC_SHOP_URL=
ENV API_URL=${API_URL} NEXT_PUBLIC_SHOP_URL=${NEXT_PUBLIC_SHOP_URL} NEXT_TELEMETRY_DISABLED=1
RUN corepack enable && corepack prepare pnpm@10.34.8 --activate
WORKDIR /repo
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/${APP}/package.json apps/${APP}/
RUN pnpm install --frozen-lockfile --filter "${APP}..."
COPY apps/${APP} apps/${APP}
RUN mkdir -p apps/${APP}/public && pnpm --filter ${APP} build

FROM node:${NODE_VERSION}
ARG APP
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0 APP=${APP}
WORKDIR /app
COPY --from=build --chown=node:node /repo/apps/${APP}/.next/standalone ./
COPY --from=build --chown=node:node /repo/apps/${APP}/.next/static ./apps/${APP}/.next/static
COPY --from=build --chown=node:node /repo/apps/${APP}/public ./apps/${APP}/public
USER node
CMD ["sh", "-c", "node apps/${APP}/server.js"]
