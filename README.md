# Bootie

BOOTHの新着商品をスクレイピングして、Misskeyのノートとして流すアプリケーション

## 機能

- BOOTHの新着商品（VRChatタグ付き）を定期的にスクレイピング
- スクレイピングした商品情報をPostgreSQLに保存
- 未投稿の商品をMisskeyに自動投稿
- fs2を使用した効率的なストリーム処理
- 投稿間隔・並列度の制御

## 技術スタック

- Scala 3.5.2
- cats-effect
- fs2
- http4s (Ember client)
- circe (JSON処理)
- scribe (ロギング)
- ScalikeJDBC (データベースアクセス)
- Flyway (スキーマ管理)
- PostgreSQL
- jsoup (HTMLパース)

## セットアップ

### 前提条件

- JDK 11以上
- sbt 1.10.6以上
- PostgreSQL 12以上

### データベースの準備

```bash
# PostgreSQLでデータベースとユーザーを作成
createdb bootie
createuser bootie -P
```

### 設定ファイル

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

### ビルドと実行

```bash
# 依存関係のダウンロードとコンパイル
sbt compile

# 実行
sbt run
```

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
    └── BoothService.scala        # ビジネスロジック
```

### 設計原則

- **Effect型の抽象化**: IOモナドを直接使用せず、F[_]で抽象化
- **副作用の分離**: ScalikeJDBCなどの副作用は`Sync.delay`で純粋な世界と分離
- **fs2ストリーム**: 定期実行や並列処理にfs2を活用
- **型安全性**: Scala 3の機能を活用した型安全なコード

## 不明点・仮置き事項

以下の点については仮置きで実装しています：

1. **MisskeyのインスタンスURL・トークン**: 環境変数または設定ファイルから読み込む形で実装
2. **BOOTHのスクレイピング詳細**: jsoupを使用してHTMLパース。タグの取得方法はBOOTHのHTML構造に依存
3. **重複チェック**: `booth_item_id`をユニークキーとして使用
4. **投稿フォーマット**: 商品名、ショップ名、価格、タグ、URLを含む形式
5. **エラーハンドリング**: 各処理でエラーをログに記録し、継続実行

## ライセンス

未定
