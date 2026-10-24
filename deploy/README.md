# 떨고래 마켓 서버 배포 (Oracle Cloud 무료 서버 + 카페24 도메인)

```
고객   → https://ddgorae.ksaria.co.kr        ┐
관리자 → https://admin.ddgorae.ksaria.co.kr  ┴→ Oracle 서버 [Caddy(HTTPS) → 쇼핑몰·관리자 → API → PostgreSQL·Redis]
```

서버 한 대에 Docker로 모두 띄운다. HTTPS 인증서는 Caddy가 자동으로 발급·갱신한다. API(18080)·DB 포트는 외부에 열지 않는다.

## 1. Oracle Cloud 서버 만들기

1. https://www.oracle.com/cloud/free 가입. **홈 리전은 춘천(ap-chuncheon-1) 또는 서울(ap-seoul-1)**로 고른다 (나중에 못 바꿈).
2. (권장) 결제 → **Pay As You Go로 업그레이드**: 무료 한도 안에서는 0원이고, 쓰지 않는 무료 서버가 회수되는 것을 막는다.
3. 컴퓨트 → 인스턴스 → 인스턴스 생성
   - 이미지: **Canonical Ubuntu 24.04** (aarch64)
   - 구성(Shape): **Ampere → VM.Standard.A1.Flex, OCPU 4 / 메모리 24GB** (무료 한도 전체)
   - SSH 키: "키 쌍 생성"으로 개인키를 내려받아 보관하거나, 내 공개키(`~/.ssh/id_rsa.pub`)를 붙여 넣는다.
   - 부트 볼륨: 100GB 정도
   - "Out of capacity" 오류가 나면 시간을 두고 다시 시도하거나 OCPU 2 / 12GB로 줄여서 만든다.
4. 만든 인스턴스의 **공인 IP**를 메모한다.
5. 네트워킹 → 가상 클라우드 네트워크 → 서브넷 → 보안 목록 → 수신 규칙 추가
   - 소스 `0.0.0.0/0`, TCP, 대상 포트 `80` / `443` (두 개)

## 2. 카페24 DNS 연결

카페24 → 나의 서비스 관리 → 도메인 → `ksaria.co.kr` → **DNS 관리(A 레코드)**

| 호스트 | 값 |
|---|---|
| `ddgorae` | 서버 공인 IP |
| `admin.ddgorae` | 서버 공인 IP |

2단계 하위 주소(`admin.ddgorae`)가 안 되면 `ddgorae-admin`으로 만들고 `.env`의 `ADMIN_DOMAIN`도 그 주소로 바꾼다. 반영까지 몇 분~몇 시간. 확인: `nslookup ddgorae.ksaria.co.kr`

## 3. 서버 설치와 실행

```bash
ssh -i <개인키> ubuntu@<공인IP>

git clone https://github.com/ksasolution1-creator/AIShoping.git
cd AIShoping/deploy
bash setup-server.sh          # Docker 설치, 서버 방화벽 80·443 열기, 스왑, 시간대
exit                          # 다시 접속해야 docker 권한이 적용됨

ssh -i <개인키> ubuntu@<공인IP>
cd AIShoping/deploy
cp .env.example .env
nano .env                     # 값 채우기 (아래 참고)
docker compose up -d --build  # 처음 빌드는 10분 정도
docker compose logs -f caddy  # 인증서 발급 확인 (certificate obtained)
```

`.env`에 넣을 값

- `DB_PASSWORD`: `openssl rand -base64 24`
- `FIELD_ENCRYPTION_KEY`: `openssl rand -base64 32` (**한 번 정하면 바꾸지 말 것.** 잃어버리면 저장된 연락처·주소를 복구할 수 없다. 안전한 곳에 따로 보관)
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`: 최초 관리자 (10자 이상). 계정이 만들어진 뒤에는 지워도 된다.
- `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY`: 테스트 서버는 토스 테스트 키

## 4. 백업 (매일 새벽 3시 30분)

```bash
chmod +x ~/AIShoping/deploy/backup.sh
crontab -e
# 아래 줄 추가
30 3 * * * /home/ubuntu/AIShoping/deploy/backup.sh >> /home/ubuntu/backup.log 2>&1
```

`~/backups`에 DB와 업로드 사진이 14일치 쌓인다. 서버가 통째로 사라지는 경우에 대비해 가끔 내 PC로도 내려받아 둔다:
`scp -i <개인키> ubuntu@<공인IP>:backups/db-*.sql.gz .`

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
