# 배포 (CD)

`main` 브랜치가 배포용이다. `dev` → `main` PR이 머지되면 변경된 쪽(백엔드/프론트)만 배포된다.

## 구성

```
GitHub Actions ──ssh/scp──▶ EC2 app 서버
                             ├─ nginx :80
                             │   ├─ /        → /var/www/delipot/current  (프론트 정적 파일)
                             │   └─ /api/    → 127.0.0.1:8080            (백엔드 프록시)
                             └─ systemd delipot-backend
                                 └─ java -jar /opt/delipot/backend/current.jar
```

- 프론트와 백엔드가 같은 오리진이라 **운영에서 CORS가 없다**. `VITE_API_BASE_URL`도 비워둔다.
- 릴리스는 커밋 sha 단위로 남기고 `current` 심링크만 바꾼다. 각각 최근 5개만 보관한다.
- DB 접속정보는 서버의 `/etc/delipot/backend.env`에만 있다. CI/CD는 이 파일을 건드리지 않는다.

## 처음 한 번만 (서버 세팅)

app 서버에 JDK 21이 설치돼 있어야 한다. 나머지(nginx, systemd, 디렉토리, 권한)는 스크립트가 한다.

```bash
scp -i ~/.ssh/delipot-app.pem infra/bootstrap-app.sh ec2-user@<APP_HOST>:/tmp/
```

```bash
ssh -i ~/.ssh/delipot-app.pem ec2-user@<APP_HOST> 'sudo bash /tmp/bootstrap-app.sh'
```

그다음 서버에서 DB 접속정보를 채운다.

```bash
sudo vi /etc/delipot/backend.env
```

## 처음 한 번만 (GitHub Secrets)

저장소 Settings → Secrets and variables → Actions에 등록한다. **CLI로 등록할 때 아래 3개를 직접 실행해라 —
pem 파일 내용은 사람 손으로만 넣는다.**

```bash
gh secret set EC2_HOST --body "<app 서버 퍼블릭 IP 또는 도메인>"
```

```bash
gh secret set EC2_USER --body "ec2-user"
```

```bash
gh secret set EC2_SSH_KEY < ~/.ssh/delipot-app.pem
```

- `VITE_API_BASE_URL`은 프론트를 다른 도메인(S3/CloudFront 등)에 올릴 때만 추가한다. 같은 EC2면 필요 없다.
- `delipot-db.pem`, `delipot-nat.pem`은 CD에 쓰지 않는다. DB/NAT 인스턴스에 사람이 직접 붙을 때만 쓴다.

## 보안 체크

- pem 파일 권한이 `644`면 ssh가 거부한다. 로컬에서 한 번 조여둬라.

```bash
chmod 400 ~/.ssh/delipot-*.pem
```

- app 서버 보안그룹의 22번 포트가 `0.0.0.0/0`이면 GitHub Actions 러너 IP가 고정이 아니라서 그렇게 열어둔 것이다.
  해커톤 기간엔 그대로 가도 되지만, 끝나면 닫거나 아래 중 하나로 좁히는 게 맞다.
  - GitHub 공개 IP 범위(`https://api.github.com/meta`의 `actions`)만 허용 — 범위가 넓고 자주 바뀐다
  - AWS SSM Session Manager로 전환 — 22번을 완전히 닫을 수 있다 (권장하지만 세팅이 더 든다)

## 롤백

```bash
ssh -i ~/.ssh/delipot-app.pem ec2-user@<APP_HOST>
```

```bash
ls -1t /opt/delipot/backend/releases
```

```bash
ln -sfn /opt/delipot/backend/releases/<이전sha>.jar /opt/delipot/backend/current.jar && sudo systemctl restart delipot-backend
```

프론트는 `ln -sfn /var/www/delipot/releases/<이전sha> /var/www/delipot/current && sudo systemctl reload nginx`.

## 배포가 실패했을 때 볼 것

```bash
sudo journalctl -u delipot-backend -n 100 --no-pager
```

```bash
sudo tail -50 /var/log/nginx/error.log
```

`GET /api/health`의 `database`가 `DOWN`이면 앱이 아니라 DB 쪽(보안그룹, `backend.env`)을 본다.
