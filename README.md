# Bootie

BOOTHの新着商品をスクレイピングして、Misskeyのノートとして流すアプリケーション

## 機能

- BOOTHの新着商品（VRChatタグ付き）を定期的にスクレイピング
- スクレイピングした商品情報をPostgreSQLに保存
- 未投稿の商品をMisskeyに自動投稿
- fs2を使用した効率的なストリーム処理
- 投稿間隔・並列度の制御
- **指数バックオフによるリトライ機能**（最大5回、初期遅延1秒、最大遅延30秒）
- **失敗したアイテムの自動再キューイング**（次回バッチで再試行）

## 技術スタック

- **Scala 3.7.4**
- **JDK 25**
- cats-effect
- cats-retry（リトライ機能）
- fs2
- http4s (Ember client)
- circe (JSON処理)
- scribe (ロギング)
- ScalikeJDBC (データベースアクセス)
- Flyway (スキーマ管理)
- **PostgreSQL 18**
- jsoup (HTMLパース)

## セットアップ

### 前提条件

#### ローカル環境
- JDK 25
- sbt 1.10.6以上
- PostgreSQL 18

#### Docker環境（推奨）
- Docker
- Docker Compose

### Docker Composeを使った起動（推奨）

最も簡単な起動方法です：

```bash
# 環境変数を設定（.envファイルを作成）
cat > .env << 'EOF'
MISSKEY_INSTANCE_URL=https://your-misskey-instance.example.com
MISSKEY_TOKEN=your_misskey_token_here
POSTING_INTERVAL_SECONDS=30
POSTING_PARALLELISM=1
EOF

# コンテナをビルドして起動
docker-compose up -d

# ログを確認
docker-compose logs -f app
```

サービスを停止する場合：

```bash
docker-compose down
```

データベースも削除する場合：

```bash
docker-compose down -v
```

### ローカル環境でのセットアップ

#### データベースの準備

```bash
# PostgreSQL 18でデータベースとユーザーを作成
createdb bootie
createuser bootie -P
```

#### 設定ファイル

`src/main/resources/application.conf` を編集するか、環境変数を設定してください：

```bash
# データベース接続
export DATABASE_URL="jdbc:postgresql://localhost:5432/bootie"
export DATABASE_USER="bootie"
export DATABASE_PASSWORD="your_password"

# Misskey設定
export MISSKEY_INSTANCE_URL="https://your-misskey-instance.example.com"
export MISSKEY_TOKEN="your_misskey_token"

# 投稿設定（オプション）
export POSTING_INTERVAL_SECONDS=30
export POSTING_PARALLELISM=1
```

#### ビルドと実行

```bash
# 依存関係のダウンロードとコンパイル
sbt compile

# 実行
sbt run

# Dockerイメージのビルド
sbt stage
docker build -t bootie:latest .
```

## 設定項目

| 環境変数 | 説明 | デフォルト値 |
|---------|------|------------|
| `DATABASE_URL` | PostgreSQL接続URL | `jdbc:postgresql://localhost:5432/bootie` |
| `DATABASE_USER` | データベースユーザー名 | `bootie` |
| `DATABASE_PASSWORD` | データベースパスワード | `bootie` |
| `MISSKEY_INSTANCE_URL` | MisskeyインスタンスのURL | `https://misskey.example.com` |
| `MISSKEY_TOKEN` | Misskeyアクセストークン | - |
| `POSTING_INTERVAL_SECONDS` | 投稿間隔（秒） | `30` |
| `POSTING_PARALLELISM` | 並列投稿数 | `1` |

## アーキテクチャ

### ディレクトリ構造

```
src/main/scala/com/github/kisaragieffective/bootie/
├── Main.scala                    # エントリーポイント
├── client/
│   └── MisskeyClient.scala       # Misskey API クライアント
├── config/
│   └── AppConfig.scala           # 設定管理
├── db/
│   └── DatabaseInitializer.scala # データベース初期化
├── domain/
│   └── BoothItem.scala           # ドメインモデル
├── repository/
│   └── BoothItemRepository.scala # データベースアクセス層
├── scraper/
│   └── BoothScraper.scala        # BOOTHスクレイピング
└── service/
    └── BoothService.scala        # ビジネスロジック（リトライ機能含む）
```

### 設計原則

- **Effect型の抽象化**: IOモナドを直接使用せず、F[_]で抽象化
- **副作用の分離**: ScalikeJDBCなどの副作用は`Sync.delay`で純粋な世界と分離
- **fs2ストリーム**: 定期実行や並列処理にfs2を活用
- **型安全性**: Scala 3の機能を活用した型安全なコード
- **エラーハンドリング**: cats-retryによる指数バックオフリトライと自動再キューイング

### エラーハンドリング戦略

1. **指数バックオフリトライ**
   - 初回失敗時: 1秒待機して再試行
   - 2回目失敗時: 2秒待機して再試行
   - 3回目失敗時: 4秒待機して再試行
   - 4回目失敗時: 8秒待機して再試行
   - 5回目失敗時: 16秒待機して再試行
   - 最大遅延: 30秒

2. **自動再キューイング**
   - 5回のリトライ後も失敗した場合、アイテムはデータベース上で`posted_to_misskey=false`のまま
   - 次回の投稿バッチで自動的に再取得され、再度投稿を試行
   - これにより、一時的なネットワークエラーやAPI障害からも自動的に回復

## 動作の流れ

1. **スクレイピング処理**（10秒間隔）
   - BOOTHの新着商品ページから商品情報を取得
   - VRChatタグがついている商品のみをフィルタリング
   - データベースに未登録の商品を保存
   - エラー時は指数バックオフでリトライ

2. **投稿処理**（30秒間隔、デフォルト）
   - データベースから未投稿の商品を取得（最大10件/バッチ）
   - 設定された間隔と並列度で投稿
   - 各投稿は指数バックオフでリトライ
   - 全リトライ失敗時は次回バッチで再試行

## トラブルシューティング

### データベース接続エラー

```
Error: Connection refused
```

Docker Composeを使用している場合は、PostgreSQLコンテナが起動するのを待ってください：

```bash
docker-compose logs postgres
```

### Misskey投稿エラー

トークンが正しいか確認してください：

```bash
curl -X POST https://your-instance.example.com/api/i \
  -H "Content-Type: application/json" \
  -d '{"i": "your_token_here"}'
```

### スクレイピングエラー

BOOTHのHTML構造が変更された可能性があります。ログを確認してください：

```bash
docker-compose logs -f app | grep ERROR
```

## 開発

### テストの実行（将来追加予定）

```bash
sbt test
```

### コードフォーマット

```bash
sbt scalafmt
```

## 不明点・実装の詳細

以下の点については実装時に決定しています：

1. **MisskeyのインスタンスURL・トークン**: 環境変数または設定ファイルから読み込む
2. **BOOTHのスクレイピング詳細**: jsoupを使用してHTMLパース。タグの取得方法はBOOTHのHTML構造に依存
3. **重複チェック**: `booth_item_id`をユニークキーとして使用
4. **投稿フォーマット**: 商品名、ショップ名、価格、タグ、URLを含む日本語形式
5. **エラーハンドリング**: cats-retryで指数バックオフリトライを実装、失敗後は自動再キューイング
6. **リトライポリシー**: 最大5回、初期遅延1秒、最大遅延30秒

## ライセンス

未定
