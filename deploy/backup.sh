#!/usr/bin/env bash
# DB와 업로드 사진을 매일 백업한다 (14일 보관).
# crontab 예: 30 3 * * * /home/ubuntu/AIShoping/deploy/backup.sh >> /home/ubuntu/backup.log 2>&1
set -euo pipefail

cd "$(dirname "$0")"
BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M)"
mkdir -p "$BACKUP_DIR"

# DB: 압축된 덤프 (복구: gunzip -c 파일 | docker compose exec -T postgres psql -U aishop -d aishop)
docker compose exec -T postgres pg_dump -U aishop -d aishop --no-owner | gzip > "$BACKUP_DIR/db-$STAMP.sql.gz"

# 업로드 사진
docker compose exec -T api tar -czf - -C /app uploads > "$BACKUP_DIR/uploads-$STAMP.tar.gz"

find "$BACKUP_DIR" -type f -mtime +"$KEEP_DAYS" -delete
echo "$(date '+%F %T') 백업 완료: $BACKUP_DIR (db-$STAMP, uploads-$STAMP)"
