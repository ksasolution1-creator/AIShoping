# 떨고래 마켓 서버 배포 (Vultr 서버 + 카페24 도메인)

```
고객   → https://ddgorae.ksaria.co.kr        ┐
관리자 → https://ddgorae-admin.ksaria.co.kr  ┴→ Vultr 서버 [Caddy(HTTPS) → 쇼핑몰·관리자 → API → PostgreSQL·Redis]
```

서버 한 대에 Docker로 모두 띄운다. HTTPS 인증서는 Caddy가 자동으로 발급·갱신한다. API(18080)·DB 포트는 외부에 열지 않는다.

## 1. Vultr 서버 만들기

1. https://www.vultr.com 가입 → Deploy → **Cloud Compute - Shared CPU**
2. 위치 **Seoul**, 이미지 **Ubuntu 24.04**, 요금제 **vc2-2c-4gb** (2 vCPU / 4GB, 월 $20)
3. SSH Keys에 내 공개키(`~/.ssh/id_ed25519.pub`)를 등록한다 (안 하면 서버 상세 화면의 root 비밀번호로 접속). Automatic Backups는 끈다 (아래 4번 백업 스크립트를 쓴다).
4. 만든 서버의 **공인 IP**를 메모한다. Vultr Ubuntu는 서버 안의 ufw가 SSH만 열어 두므로, `setup-server.sh`가 80·443을 추가로 연다.

> Oracle Cloud 무료 서버(Ampere A1)를 쓸 때도 아래 절차는 같다. 그때는 `ubuntu` 계정으로 접속하고, 서브넷 보안 목록에 80·443 수신 규칙을 추가한다.

## 2. 카페24 DNS 연결

카페24 → 나의 서비스 관리 → 도메인 → `ksaria.co.kr` → **DNS 관리(A 레코드)**

| 호스트 | 값 |
|---|---|
| `ddgorae` | 서버 공인 IP |
| `ddgorae-admin` | 서버 공인 IP |

반영까지 몇 분~몇 시간. 확인: `nslookup ddgorae.ksaria.co.kr`, `nslookup ddgorae-admin.ksaria.co.kr`

## 3. 서버 설치와 실행

```bash
ssh root@<공인IP>

git clone https://github.com/ksasolution1-creator/AIShoping.git
cd AIShoping/deploy
bash setup-server.sh          # Docker 설치, 서버 방화벽 80·443 열기, 스왑, 시간대

cd ~/AIShoping/deploy
cp .env.example .env
nano .env                     # 값 채우기 (아래 참고)
docker compose up -d --build  # 처음 빌드는 10분 정도
docker compose logs -f caddy  # 인증서 발급 확인 (certificate obtained)
```

`.env`에 넣을 값

- `DB_PASSWORD`: `openssl rand -base64 24`
- `FIELD_ENCRYPTION_KEY`: `openssl rand -base64 32` (**한 번 정하면 바꾸지 말 것.** 잃어버리면 저장된 연락처·주소를 복구할 수 없다. 안전한 곳에 따로 보관)
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`: 최초 관리자 (10자 이상). 계정이 만들어진 뒤에는 지워도 된다.
- `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY`: 테스트 서버는 토스 테스트 키. 카드 결제는 `CARD_PAYMENT_ENABLED=true`일 때만 열린다
- `BANK_*`: 무통장입금 계좌와 입금 기한. 비우면 기본값(기업은행 회사 계좌, 48시간)을 쓴다
- `MAIL_*`: 알림 메일 SMTP. 비우면 메일 없이 동작한다. 운영자 알림은 `NOTIFY_ADMIN_EMAIL`(기본 kmh@ksas.co.kr)로 간다

### 간편로그인 (카카오·네이버)

키를 넣지 않으면 로그인 화면의 버튼이 '준비중'으로 보이고, 나머지 기능은 그대로 동작한다.

**카카오** — https://developers.kakao.com → 내 애플리케이션 → 애플리케이션 추가
- 앱 키 → **REST API 키**를 `KAKAO_CLIENT_ID`에
- 카카오 로그인 → 활성화 ON, Redirect URI에 `https://ddgorae.ksaria.co.kr/api/auth/social/kakao/callback`
- 카카오 로그인 → 동의항목: 닉네임(필수), 카카오계정(이메일)(선택). 이름은 비즈 앱 전환 후 받을 수 있다
- 보안 → Client Secret을 켰다면 그 값을 `KAKAO_CLIENT_SECRET`에 (끄면 비워 둔다)
- 플랫폼 → Web 사이트 도메인에 `https://ddgorae.ksaria.co.kr`

**네이버** — https://developers.naver.com → Application → 애플리케이션 등록
- 사용 API: 네이버 로그인, 제공 정보: 이름·이메일(필수), 별명(선택)
- 서비스 URL `https://ddgorae.ksaria.co.kr`, Callback URL `https://ddgorae.ksaria.co.kr/api/auth/social/naver/callback`
- Client ID → `NAVER_CLIENT_ID`, Client Secret → `NAVER_CLIENT_SECRET`
- 처음에는 '개발 중' 상태라 멤버로 등록한 네이버 아이디만 로그인된다. 검수 요청이 통과해야 모든 고객이 쓸 수 있다

로컬에서 시험할 때는 같은 앱에 `http://localhost:3000/api/auth/social/{kakao|naver}/callback`도 등록하고, `apps/api/config/application.yml`의 `aishop.social` 아래에 키를 넣는다.

`.env`를 고친 뒤 `docker compose up -d api`로 API만 다시 띄우면 적용된다.

## 4. 백업 (매일 새벽 3시 30분)

```bash
chmod +x ~/AIShoping/deploy/backup.sh
crontab -e
# 아래 줄 추가
30 3 * * * $HOME/AIShoping/deploy/backup.sh >> $HOME/backup.log 2>&1
```

`~/backups`에 DB와 업로드 사진이 14일치 쌓인다. 서버가 통째로 사라지는 경우에 대비해 가끔 내 PC로도 내려받아 둔다:
`scp root@<공인IP>:backups/db-*.sql.gz .`

## 5. 업데이트 배포

```bash
cd ~/AIShoping && git pull
cd deploy && docker compose up -d --build
```

DB 구조 변경(Flyway)은 API가 시작할 때 자동으로 적용된다.

## 자주 쓰는 명령

| 할 일 | 명령 |
|---|---|
| 상태 보기 | `docker compose ps` |
| 로그 보기 | `docker compose logs -f api` (web, admin, caddy) |
| 다시 시작 | `docker compose restart api` |
| 전체 중지 | `docker compose down` (데이터는 남음) |
| DB 접속 | `docker compose exec postgres psql -U aishop -d aishop` |
