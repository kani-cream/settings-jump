# Settings Jump 設計書

- Status: Draft
- Version: 0.7
- Repository: `kani-cream/settings-jump`
- Product name: **Settings Jump**

## 変更履歴

| Version | 概要 |
|---|---|
| 0.1 | 初版 |
| 0.2 | 設計レビューを反映。Eligible Settings Page 境界を定義し、PoC Gate を定量的 GO/NO-GO 判定へ強化。Stable ID / 表示名取得 / 階層構築 / ナビゲーション経路の前提を現行 SDK と照合して修正。対象環境・ビルド基盤・スレッディング・永続化スキーマ・テスト実行形態を確定。リリース計画を再構成 |
| 0.3 | 第2回レビューを反映。Eligible(識別の安定性)と Available(現在の context での存在)を分離し `ConfigurableProvider` による contextual availability に対応。Index を Application / Project の二層に分離。Gate 1/2 の reference 測定方法を明記(Internal API 禁止は配布 runtime code への制約と明確化)。日本語 UI での英語検索の保証範囲を限定。Nested static Configurable の親子解決を明記 |
| 0.4 | 第3回レビューを反映。Navigation を preflight + navigation の二段構成にし、`ShowSettingsUtil` の predicate 不一致例外を IDE へ漏らさない fail closed 境界を明記。二層 index の役割を「scope と lifecycle の分離」に修正(availability は Navigation 時のみ評価)。`childrenEPName` を v1.0 Non-eligible と定義。節参照の修正、v0.5 / v1.0 のリリース名称変更 |
| 0.5 | Platform ソース照合による精密化。SDK 上 `id` / display metadata は推奨であり必須ではないと訂正(Eligible 条件は Platform 要件より意図的に厳しいと明記)。bundle 省略時は plugin descriptor default bundle で key を解決できるため Eligible 判定に含める。`nonDefaultProject` により EP 直接宣言でも context 依存になり得るため CONTEXTUAL の定義を拡張し、preflight は AvailabilityKind に関係なく全ページで実施(AvailabilityKind は UI hint)。preflight に `ep.isAvailable()` を追加。例外方針を明確化(Platform cancellation は再送出) |
| 0.6 | リリース計画を Phase 0(Technical Validation・version なし)→ v0.5(Public Preview・全機能)→ v1.0(Stable・安定化のみ)の3段階に簡素化。中間バージョン(v0.1〜v0.4、v0.9)を廃止し、進行管理はコミット・Issue 単位とする |
| 0.7 | 将来候補にマウス向けエントリポイント(ツールバー / ステータスバーのアイコンから Search Popup を開く。Shortcut Slot と併用)を追加。Tool Window 不採用の判断は維持 |

---

## 1. 概要

Settings Jump は、JetBrains IDE の階層化された Settings / Preferences へ高速にアクセスするための IntelliJ Platform プラグインである。

JetBrains IDE の Settings は機能が豊富である一方、目的の設定ページが深い階層に存在することが多い。ユーザーは設定値そのものを変更したいのではなく、まず「どこにあるか」を探し、階層を辿る必要がある。

### 1.1 中心ステートメント

> **Settings Jump は、IntelliJ Platform の公開 API と安定した識別子のみを用いて、安全に再識別・再オープンできる Settings ページを高速に検索、Favorite 化、Shortcut 化する。**

このステートメントが全設計判断の基準である。以下のいずれかに該当するページは、機能を追加して無理に対応するのではなく、**対象外とする**。

- 安定 ID を持たない
- 公開メタデータから表示名・階層を解決できない
- Dynamic に生成される
- scope を判定できない
- 公開 API で再オープンできない
- Internal API がなければ扱えない

対応率 100% を目指さない。「対象としたページは確実に動く」ことを優先する。

### 1.2 提供する操作

1. Settings ページを名前・階層から検索する
2. 検索結果から目的の Settings ページを直接開く
3. よく使う Settings ページを Favorite として登録する
4. Favorite に固定ショートカットスロットを割り当て、1操作で開く
5. 最近開いた Settings ページへ再アクセスする

本プラグインは **Settings の値を変更しない**。

設定値の表示、編集、検証、Apply / Reset、永続化、再起動要否などの責務は JetBrains IDE または各プラグイン自身の Settings UI に残す。

---

## 2. 解決したい問題

### 2.1 現状の問題

JetBrains IDE では Settings が多段階に階層化されている。

例:

```text
Settings
└── Build, Execution, Deployment
    └── Build Tools
        └── Gradle
```

ユーザーが目的のページ名を覚えていても、毎回親カテゴリから辿る必要がある。設定場所を完全には覚えていない場合はさらに探索コストが発生する。

### 2.2 標準機能との差別化

IDE には Settings ダイアログ内検索や Search Everywhere が既に存在するため、「検索して開く」単体では標準機能との差が小さい。

Settings Jump の独自価値は以下にある。

```text
Search             = 基盤(前提機能)
Favorites          = 価値
Shortcut Slots     = コア価値
Recent             = 補助価値
```

「Gradle Settings を検索できる」ではなく、**「Gradle Settings を好きなキー1発で開ける」**が製品の中心である。この優先順位はリリース計画(24節)に反映する。

---

## 3. プロダクト原則

### 3.1 Navigation, not Configuration

Settings Jump は Settings の値を変更しない。

以下は行わない。

- Checkbox の ON / OFF
- ComboBox の選択変更
- TextField の書き換え
- Registry 値の変更
- 他プラグイン設定値の変更
- Apply / Reset の代行
- Settings UI の Swing component を探索して疑似操作する処理

### 3.2 公開 API を優先する

IntelliJ Platform の公開 API を使用する。

`@ApiStatus.Internal`、非推奨 API、UI 実装詳細への依存は原則禁止する。

内部 API がなければ実装できない機能は、実装範囲から除外する。

### 3.3 Fail safely / Fail closed

IDE バージョン変更、プラグインの追加・削除などにより保存済み Settings ページが存在しなくなった場合でも、IDE の動作を妨げてはならない。

ページが見つからない場合は Favorite / Recent を壊さず、利用不可として扱う。

「たぶん同じページ」を推定して開くことはしない。**確証がなければ開かない**。誤った Settings を開くことは、開けないことより危険である。

### 3.4 IDE の Settings を唯一の編集 UI とする

Settings Jump 側に設定項目のコピー UI を作らない。

設定変更に関するテスト責務を Settings Jump が引き受けないことを、設計上の重要な境界とする。

### 3.5 Eligible Page のみを正式対象とする

全 Settings ページへの対応を目指さない。4節で定義する Eligible Settings Page のみを Search / Favorite / Recent / Shortcut の対象とする。

---

## 4. Eligible Settings Page

Settings Jump が正式に扱うページの条件を定義する。**以下すべてを満たすページのみ**が Eligible である。

```text
Eligible Settings Page

1. applicationConfigurable / projectConfigurable EP 由来である
2. stable ID が取得可能である
3. displayName、または localization key と解決可能な Resource Bundle
   (explicit bundle / plugin descriptor default bundle のいずれか)が
   EP メタデータから取得可能である
4. scope(APPLICATION / PROJECT)が判定可能である
5. parent chain が公開メタデータから解決可能である
6. public な ShowSettingsUtil 経由で再オープン可能である
```

### 4.1 SDK 上の前提と現実

現行 SDK では `applicationConfigurable` / `projectConfigurable` の `id` および display metadata(`displayName` または `key`)の宣言が**推奨されているが、必須ではない**(`ConfigurableEP` 上、`id` / `displayName` / `parentId` に `@RequiredElement` は付いていない。`parentId` は完全に optional であり、親子関係は nested `<configurable>` や `groupId` でも表現できる)。

また Platform 実装は歴史的経緯から寛容であり、EP に ID がない場合のフォールバック(`SearchableConfigurable#getId()` → `providerClass` / `instanceClass` 等)が存在する。素の `Configurable` インターフェース自体には ID の契約がない(`SearchableConfigurable` が `ConfigurableWithId` を継承する構造)。

したがって:

- **SDK 準拠の正常なページであっても、Settings Jump の Eligible 条件を満たさない場合がある**
- **Settings Jump の Eligible 条件は、IntelliJ Platform 自体の Configurable 要件より意図的に厳しい**

```text
IntelliJ として有効
      ≠
Settings Jump で永続化可能
```

この差は設計思想(3.3 fail closed)と整合しており、埋めようとしない。

なお displayName の解決について、`bundle` 属性が省略されている場合でも Platform は plugin descriptor の default Resource Bundle(`getResourceBundleBaseName()`)から `key` を解決する。この公開された bundle 解決規則で localized display name を取得できるページは Eligible とし、不必要に Non-eligible 判定しない。

### 4.2 Non-eligible ページの扱い

```text
Eligible
→ Search 対象
→ Favorite 可
→ Recent 可
→ Shortcut Slot 可

Non-eligible
→ v1.0 では一切の対象外
```

クラス名 + 親 ID などの**合成キーによる救済は行わない**。永続化できる確証がないページは永続化対象にしない。これが fail closed 原則(3.3)と最も整合する。

### 4.3 Eligible と Available の分離

Eligible は「識別の安定性」に関する性質であり、「今この瞬間に開けるか」を保証するものではない。両者を明確に分離する。

```text
Eligible
= Settings Jump が安全に識別・永続化できるページ
  (identity は Application 的に安定)

Available
= 現在の Project / context で実際に開けるページ
```

分離が必要な理由: `ConfigurableProvider` は、実行環境によって Settings ページを出したり出さなかったりするための**正式な仕組み**である(`canCreateConfigurable()` が現在の context で表示すべき場合のみ true を返す。例: JetBrains 本体の `VcsManagerConfigurableProvider` は利用可能な VCS が存在する Project でのみ true)。

つまり EP メタデータ上は完全に Eligible に見えるページが、

```text
Project A → canCreateConfigurable() = true
Project B → canCreateConfigurable() = false
```

となり得る。これは設計の矛盾ではなく、「ページの identity は安定しているが、そのページが今存在するかは Project context に依存し得る」という性質として扱う。

なお context 依存は Provider に限らない。EP 直接宣言でも `nonDefaultProject` 属性により default project では利用不可になり得る(`ConfigurableEP.isAvailable()`)。

```kotlin
enum class AvailabilityKind {
    STATIC,      // metadata 上、特別な context 依存が確認できない
    CONTEXTUAL   // providerClass != null または nonDefaultProject == true
}
```

`AvailabilityKind` は **UI に「このページは context 依存かもしれない」と表示するための hint に留める**。実際の availability 判定は AvailabilityKind に関係なく、Navigation 時の preflight(10.2節)で全ページに対して行う。

挙動:

- CONTEXTUAL なページも **Eligible であり、Favorite / Slot 登録を禁止しない**
- Navigation 時に現在の context で resolve できなければ fail closed(21節)
- UI 上は「この設定はプロジェクトによって利用できない場合がある」として扱えれば十分とする

### 4.4 Eligible 率の検証

Non-eligible ページが実際にどの程度存在するかは PoC Gate 1 で定量的に測定する(18節。測定方法は Gate 1 に明記)。

日常利用される主要ページの大部分が Non-eligible と判明した場合は、**実装を工夫して救済するのではなく、企画そのものを再評価する**。

---

## 5. スコープ

### 5.1 v1.0 に含める

- Eligible Settings Page の検出とメタデータ index 構築
- Settings ページ検索(Localization 考慮を含む)
- 目的ページを選択した状態で Settings を開く
- Favorite 登録 / 解除
- Recent Settings
- 固定 Shortcut Slot
- Project Settings / Application Settings の適切な扱い
- 利用不可になった Settings ページの安全な処理
- JetBrains 標準 Settings の主要ページに対する互換性確認
- 通常の `applicationConfigurable` / `projectConfigurable` を使用するサードパーティープラグインの基本対応

### 5.2 v1.0 に含めない

- Settings 値の直接変更
- Non-eligible ページの救済(合成キー、推定マッチング等)
- Dynamic Configurable(`Configurable.Composite#getConfigurables()` / `dynamic=true`)の子ページ対応
- `childrenEPName` による子ページ供給の対応(8.2節。Gate 2 で出現率のみ測定)
- Boolean Toggle
- Settings Profile
- 設定値の Import / Export
- Settings 値の同期
- Settings UI 内の任意 component への強制フォーカス
- Swing component tree の解析
- Reflection による設定値変更
- Internal API に依存した全文 Settings 検索
- AI 機能

Dynamic Configurable を除外する根拠: JetBrains 公式も dynamic child は Settings ツリー構築時に追加のクラスロードを発生させるため推奨しておらず、XML 宣言を使うよう案内している。PoC Gate 2 で「日常利用ページの大部分が dynamic だった」と判明した場合のみ再検討する。

---

## 6. 想定ユーザーフロー

### 6.1 検索して開く

1. ユーザーが `Open Settings Jump` Action を実行する
2. Popup が表示される
3. 検索欄へ `gradle` と入力する
4. 一致する Settings ページが一覧表示される
5. `Build, Execution, Deployment > Build Tools > Gradle` を選択する
6. Enter を押す
7. Gradle ページが選択された Settings ダイアログを開く

### 6.2 Favorite から開く

1. Settings Jump を開く
2. Settings ページを Favorite に登録する
3. 次回以降は検索せず Favorite から選択する

### 6.3 Shortcut Slot から開く

1. Favorite を `Shortcut Slot 1` に割り当てる
2. JetBrains の Keymap で `Settings Jump: Shortcut 1` にキーを割り当てる
3. キー入力だけで対象 Settings ページを開く

### 6.4 Recent から開く

1. Settings Jump 経由でページを開く
2. 開いたページを Recent に追加する
3. 次回 Settings Jump 起動時に Recent から再度開ける

---

## 7. UI / UX

### 7.1 メイン UI

Tool Window は使用せず、基本 UI は軽量な Search Popup とする。

```text
┌──────────────────────────────────────────────┐
│ Settings Jump                               │
│ > gradle                                    │
├──────────────────────────────────────────────┤
│ ★ Build Tools > Gradle                      │
│   Build, Execution, Deployment > ...        │
│                                              │
│   Gradle JVM                                │
│   Build, Execution, Deployment > ...        │
└──────────────────────────────────────────────┘
```

### 7.2 空検索時

検索文字列が空の場合は以下の順で表示する。

```text
Favorites
Recent
All Settings
```

All Settings は必要に応じて折りたたみ、Favorite / Recent を優先する。

### 7.3 検索結果表示

各結果は最低限以下を表示する。

- Display Name
- Navigation Path(8.2節)
- Favorite 状態
- Application / Project scope を区別する必要がある場合は scope

同名 Settings が存在しても path で判別できるようにする。

### 7.4 Keyboard-first

主要操作はキーボードだけで完結できること。

- Popup を開く
- 検索
- 上下移動
- Enter で開く
- Favorite 登録 / 解除

マウス操作は補助とする。

Popup 内での Favorite 登録 / 解除・Slot 割り当ての具体的なキーバインドは v0.5 の実装時に確定する(Phase 0 のブロッカーではない)。

---

## 8. Settings ページモデル

内部表現は Settings の値を保持せず、ナビゲーションに必要なメタデータのみ保持する。

```kotlin
data class SettingsPage(
    val id: String,              // stable ID(必須。取得不能なら Eligible でない)
    val displayName: String,
    val parentId: String?,
    val path: List<String>,      // Navigation Path(表示・検索用)
    val scope: SettingsScope,
    val sourcePluginId: String?,
    val availability: AvailabilityKind  // 4.3節
)

enum class SettingsScope {
    APPLICATION,
    PROJECT
}
```

`UNKNOWN` scope は設けない。scope を判定できないページは Eligible でないため、モデルに現れない(fail closed)。

`availability` は EP 宣言メタデータから判定する(`providerClass != null` または `nonDefaultProject == true` なら CONTEXTUAL、それ以外は STATIC)。これは UI hint であり、実際の availability 判定は preflight(10.2節)が担う(4.3節)。

### 8.1 Stable key

永続化には displayName を主キーとして使用しない。

`Configurable` / Extension Point が提供する一意 ID を stable key とする。ID 照合には `ConfigurableWithId` を使用する(`SearchableConfigurable` への限定キャストに依存しない)。

理由:

- UI 表示名は変更される可能性がある
- Localization により表示名が変化する
- 同名ページが存在し得る

stable ID を取得できないページは index に含めない(4.2節)。合成キーは作らない。

### 8.2 Navigation Path

親子関係は以下の優先順で解決し、表示用 path を構築する。

```text
Parent relationship source:

1. explicit parentId
2. nested ConfigurableEP parent
   (親 EP 宣言の内側にネストされた <configurable>。
    子自身は parentId を持たず、XML のネスト自体が親子を表現する)
3. top-level group
```

Nested static Configurable は XML 宣言由来の静的な構造であり、Dynamic ではない。子ページの供給形態は以下のとおり分類する。

```text
Nested <configurable>(XML ネスト宣言)
→ Eligible 候補

childrenEPName(別 EP を子として参照する正式な属性)
→ v1.0 では Non-eligible
→ Gate 2 で出現率のみ測定

dynamic=true
→ Non-eligible

runtime Composite child(Configurable.Composite#getConfigurables())
→ Non-eligible
```

`childrenEPName` は静的属性だが、参照先 EP の列挙が `ConfigurableEP` メタデータのみで安全に行えるかは自明でないため、v1.0 では対象外とする。Gate 2 の測定で出現率が高く、かつメタデータのみで安全に列挙できるケースが確認できた場合に v1.x で拡張を検討する。

例:

```text
Build, Execution, Deployment > Build Tools > Gradle
```

**保証範囲の明確化**: Settings Jump が構築するのは「公開 EP メタデータから解決可能な Navigation Path」であり、**Settings UI のツリー表示の完全再現ではない**。

IDE 自身の Settings ツリーは `ConfigurableExtensionPointUtil` 等の内部ユーティリティで構築されており、これと完全一致させようとすると Internal API 依存(3.2節違反)が必要になる。Settings Jump はユーザーがページを識別・判別できる path を提供できれば十分とする。

path は検索補助・表示用であり、永続化上の identity には使用しない。

---

## 9. Settings 検出

### 9.1 基本方針

`applicationConfigurable` / `projectConfigurable` EP の**宣言メタデータのみ**から index を構築する。

使用する情報:

- Configurable ID
- Display Name(または key + bundle から解決した localized name)
- Parent ID
- Group ID
- Application / Project scope
- Source plugin

**Configurable インスタンスの生成は index 構築では行わない**。EP メタデータに `displayName` / `key` が宣言されていないページは、インスタンス化して `getDisplayName()` を呼べば名前を取得できるが、それは行わず Non-eligible として除外する(4節)。

### 9.2 Index の二層構造

`applicationConfigurable` と `projectConfigurable` は寿命が異なるため、index を単一で共有せず二層に分ける。

```text
ApplicationSettingsIndex
- applicationConfigurable 由来
- Application lifecycle にキャッシュ
- 全 Project ウィンドウで共有

ProjectSettingsIndex(Project)
- projectConfigurable 由来
- Project ごとに構築・キャッシュ
- Project lifecycle に従い破棄
```

検索対象は context によって決まる。

```text
Welcome 画面(Project なし)
→ ApplicationSettingsIndex のみ

Project あり
→ ApplicationSettingsIndex
  + その Project の ProjectSettingsIndex
```

論理 API としては `SettingsPageIndex.forContext(project: Project?)` の形で統合ビューを提供する。

複数 Project ウィンドウを開いている場合、各ウィンドウの検索対象はそのウィンドウの Project の index であり、これは「Action が実行された DataContext の Project を使う」という Navigation 方針(10.3節)と一貫する。

なお、この二層分離が担うのは **scope と lifecycle の分離**である。index は EP メタデータのみから構築される(9.1節)ため、`ConfigurableProvider` 由来ページの実際の availability は index には反映されない。availability は Navigation 時の preflight(10.2節)でのみ評価する。検索結果上は CONTEXTUAL ページに「context によって利用できない場合がある」ことを示すに留め、検索のたびに Provider を評価することはしない(軽量な Settings launcher という性格を維持するため)。

### 9.3 Dynamic Settings

`dynamic=true` および `Configurable.Composite#getConfigurables()` による動的な子ページは v1.0 では対象外とする(5.2節)。

静的 EP 宣言のみで実用上十分なカバレッジがあるかは PoC Gate 2 で定量的に確認する。

### 9.4 Third-party plugin

サードパーティープラグインが通常の IntelliJ Platform Settings EP を Eligible 条件を満たす形で使用している場合は検索対象に含める。

以下は保証対象外とする。

- 独自 Dialog のみを使用するプラグイン
- Internal API に依存する Settings
- Runtime に特殊な方法で生成される UI
- 標準 Configurable tree へ登録されない設定画面
- Eligible 条件(4節)を満たさない EP 登録

### 9.5 Index の無効化と再構築

プラグインの動的 load / unload は `DynamicPluginListener` で検知する。

```text
Plugin loaded / unloaded
        ↓
index generation++(invalidate のみ。Application / Project 両層)
        ↓
次回 Popup open 時に lazy rebuild
```

イベントのたびに即座に再構築はしない。invalidate + lazy rebuild とする。

ProjectSettingsIndex は加えて Project close で破棄される(9.2節)。

---

## 10. Settings を開く処理

Settings ページへのナビゲーションは `ShowSettingsUtil` の public predicate overload を中心に実装する。

概念例:

```kotlin
ShowSettingsUtil.getInstance().showSettingsDialog(
    project,
    { configurable -> (configurable as? ConfigurableWithId)?.id == targetId },
    null
)
```

### 10.1 経路の性質と検証義務

public predicate 経路は「保存 ID を渡して O(1) で開く」API ではない。内部的には Settings ツリーを構築し、predicate に一致する Configurable を探索する。ID 指定専用の便利メソッドは `ShowSettingsUtilImpl` 側(Internal)にあり、使用しない。

したがって:

- predicate traversal の**実測性能**を PoC Gate 3 で検証する(18節)
- 探索が dynamic child の生成など想定外の副作用を起こさないことも同 Gate で確認する

### 10.2 Preflight + Navigation

`ShowSettingsUtil.showSettingsDialog(project, predicate, ...)` の現行実装は、predicate に一致する Configurable が見つからない場合に**例外を送出する**(`ConfigurableVisitor.find(...) ?: error(...)`)。つまり素朴に呼ぶだけでは Gate 3 の「fail closed(何も開かず、エラーも発生させない)」を満たせない。

そこで Navigation は **preflight + navigation** の二段構成とする。

preflight は **AvailabilityKind に関係なく全ページで実施する**(AvailabilityKind は UI hint に過ぎない。4.3節)。

```text
Shortcut / Favorite 実行
        ↓
対象 EP が現在の context に存在するか確認
        ↓
ep.isAvailable()
  (nonDefaultProject 等の metadata 由来の可用性)
        ↓
ep.canCreateConfigurable()
  (public API。provider の場合は内部で
   provider.canCreateConfigurable() が評価される)
        ↓
Unavailable
→ ShowSettingsUtil を呼ばない
→ 軽量通知のみ
        ↓
Available
→ ShowSettingsUtil で開く
```

補足:

- **index 構築時には Provider を生成しない方針(9.1節)を維持する**。availability 評価はユーザーが実際に Open した瞬間のみ行う
- preflight 通過後も plugin unload 等との race は残り得るため、`ShowSettingsUtil` 呼び出し境界を try-catch で保護し、**解決不能でも IDE へ例外を漏らさない**。失敗時は軽量通知に落とす
- ただし無差別な `catch (Throwable)` / `runCatching` にはしない。**Navigation 失敗として想定される例外のみを UI failure へ変換し、`ProcessCanceledException` / `CancellationException` 等の Platform cancellation は握り潰さず再送出する**

### 10.3 Project scope の解決

Favorite / Slot は Application-level に保存されるが(12節)、`PROJECT` scope のページを開くには Project が必要である。挙動を以下に確定する。

```text
APPLICATION page
→ Project 不要。常に開ける

PROJECT page + active project あり
→ Action が実行された DataContext の Project
  (AnActionEvent#getProject())の Settings を開く

PROJECT page + Project なし(Welcome 画面等)
→ 開かない(fail closed)
→ 軽量通知: "This setting requires an open project"

複数 Project ウィンドウ
→ Action が実行された DataContext の Project を使用する
```

### 10.4 禁止事項

以下の手法は禁止する。

- Settings dialog の component tree を直接操作する
- Settings の左 Tree を文字列一致でクリックする
- UI 座標や component hierarchy に依存する
- `ShowSettingsUtilImpl` など implementation class へ直接依存する

---

## 11. Search

### 11.1 Search index

Localization を考慮し、検索インデックスは表示名だけに依存しない。各ページについて以下を index する。

```text
visibleDisplayName     (現在の locale での表示名)
visiblePath            (現在の locale での Navigation Path)
configurableId
parentId
sourcePluginId
canonical group alias  (group ID 由来の正規化 token)
```

例(日本語 UI 環境):

```text
表示:
ビルドツール > Gradle

内部検索 token:
gradle
build
build.tools
org.jetbrains.plugins.gradle...
```

ID・group ID 等の英語由来 token を index に含めることで、日本語 UI 環境でも英語クエリ(`gradle` 等)が機能する。英語名の翻訳 DB を自前で維持する必要はない。

**保証範囲の限定**: 非英語 UI での英語検索が保証されるのは、**ID / plugin ID / canonical group alias から取得できる英語 token に限る**。表示名の英語版(例: 表示「外観」に対する "Appearance")が ID に含まれない場合、その英語名での検索は保証しない。localized label と canonical English label の両取得は v1.0 では行わない(Localization 処理が一段複雑になり、主目的から外れるため。将来候補 26節)。

### 11.2 Search normalization

最低限以下を行う。

- Case insensitive
- Trim
- 連続空白の正規化

### 11.3 Ranking

初期 ranking:

1. Exact displayName
2. Prefix displayName
3. Token match displayName
4. ID / plugin ID token match
5. Full path match
6. Partial match

Favorite / Recent は同スコア時の tie breaker として使用する。

高度な fuzzy search は v1.0 の必須要件にしない。

---

## 12. Favorites

Favorite は Application-level に保存する。

理由:

- Settings へのアクセス習慣はプロジェクト固有とは限らない
- IDE を跨いだ利用より、まず同一 IDE 内での高速アクセスを優先する

保存例:

```kotlin
data class FavoriteSettingsPage(
    val configurableId: String,
    val scope: SettingsScope,
    val lastKnownDisplayName: String,
    val lastKnownPath: String
)
```

`lastKnownDisplayName` / `lastKnownPath` は対象が消えた際の UI 表示や診断用であり identity には使用しない。

Favorite に登録できるのは Eligible Page のみ(4.2節)。

---

## 13. Recent Settings

Settings Jump 経由で開いたページを履歴へ保存する。

初期仕様:

- 最大 20 件
- 重複は最新へ移動
- Favorite とは別管理
- 対象 Configurable が存在しない場合は UI 上で利用不可として扱うか、自動整理する

JetBrains IDE 標準 Settings からユーザーが手動で開いたページの追跡は v1.0 では必須としない(Settings dialog 内部状態の監視が必要になり、責務外のため)。

---

## 14. Shortcut 設計

### 14.1 方針

任意の Favorite ごとに Runtime Action を無制限生成する方式は採用しない。

Keymap との安定した統合を優先し、固定 Slot 方式を採用する。

```text
Settings Jump: Open
Settings Jump: Shortcut 1
...
Settings Jump: Shortcut 10
```

ユーザーは Settings Jump UI で Slot と Settings ページを紐付ける。

### 14.2 初期 Slot 数

v1.0 では 10 Slot とする。必要性が確認された場合のみ増やす。

### 14.3 Slot 未設定時

未設定 Slot の Action が実行された場合は何も開かず、軽量な通知または Settings Jump Popup への誘導を行う。

### 14.4 Shortcut conflict

Keymap への実際のキー割り当ては JetBrains IDE 標準 UI に任せる。

Settings Jump 独自のキーバインドエディタは作らない。

---

## 15. 永続化

Application-level `PersistentStateComponent` を使用する。

### 15.1 Schema version

将来のマイグレーションに備え、state に schema version を初版から含める。

```kotlin
data class State(
    var schemaVersion: Int = 1,
    var favorites: MutableList<FavoriteState> = mutableListOf(),
    var recent: MutableList<RecentState> = mutableListOf(),
    var slots: MutableList<SlotState> = mutableListOf()
)
```

読み込み時に schemaVersion を検査し、未知の将来バージョンは安全側(読み捨てず、書き壊さず)で扱う。

### 15.2 保存対象

```text
Favorites
Shortcut Slot assignments
Recent Settings
Plugin preferences
```

保存しないもの:

```text
JetBrains Settings values
Third-party plugin Settings values
```

---

## 16. アーキテクチャ

```text
┌────────────────────────────┐
│        UI / Actions        │
│ Search Popup / Favorites   │
│ Shortcut Actions           │
└─────────────┬──────────────┘
              │
              ▼
┌────────────────────────────┐
│    SettingsJumpService     │
│ Search / Ranking / Open    │
└───────┬───────────┬────────┘
        │           │
        ▼           ▼
┌──────────────┐  ┌────────────────┐
│ Page Index   │  │ User State     │
│ EP metadata  │  │ Favorites      │
│ (Eligible)   │  │ Recent / Slots │
└──────┬───────┘  └────────────────┘
       │
       ▼
┌────────────────────────────┐
│ IntelliJ Platform APIs     │
│ Configurable EP metadata   │
│ ShowSettingsUtil           │
│ DynamicPluginListener      │
└────────────────────────────┘
```

### 16.1 責務

#### SettingsPageIndex(二層: Application / Project)

- Eligible Page 検出(EP メタデータのみ)
- hierarchy 構築(explicit parentId / nested EP / group)
- ID lookup
- `forContext(project)` による統合ビュー提供(9.2節)
- `DynamicPluginListener` による invalidate + lazy rebuild
- ProjectSettingsIndex は Project lifecycle に従う

#### SettingsSearchService

- Query normalization
- filtering
- ranking

#### SettingsNavigationService

- ID から Configurable を解決(`ConfigurableWithId`)
- `ShowSettingsUtil` public overload で Settings を開く
- Preflight による availability 評価(10.2節)
- Project scope 解決(10.3節)
- 解決不能時の fail closed

#### SettingsJumpState

- Favorites / Recent / Shortcut Slot
- schema versioned persistence

#### UI

- Search Popup
- Favorite 操作
- Slot 割り当て

### 16.2 スレッディングモデル

- index 構築は BGT(coroutine)で行う。EDT に重い処理を置かない
- **ReadAction は無条件には使わない**。EP メタデータの読み取りのみであれば read lock は原則不要であり、API 契約上 read lock を要求する箇所に限定して `readAction` を使用する
- 長時間の ReadAction 保持は行わない(UI の write を妨害するため)
- Popup 表示時に index が未構築・invalidated の場合は BGT で構築し、UI は構築完了まで軽量なローディング状態を示す

---

## 17. API 利用ポリシー

### 17.1 利用可

- IntelliJ Platform の public API
- documented Extension Point
- stable Configurable ID / `ConfigurableWithId`
- Action System
- `PersistentStateComponent` 等の公開永続化機構
- `DynamicPluginListener`

### 17.2 原則利用禁止

- `@ApiStatus.Internal`
- `@ApiStatus.Obsolete`
- removal 予定の deprecated API
- implementation package の直接利用(`ShowSettingsUtilImpl`、`ConfigurableExtensionPointUtil` の Internal メソッド等)
- Reflection による private field / method 参照
- Swing component structure 依存

**適用範囲**: 上記の禁止は**配布 Plugin の runtime code に対する制約**である。PoC Gate の測定専用コード(18節 Gate 1)およびテストコードには適用しない。ただし測定・テストコードが配布物へ混入しないことをビルド構成で保証する。

なお Starter + Driver の UI テスト部分は現在 Experimental 扱いだが、**製品 runtime ではなくテスト限定依存**であるため使用を認める。

### 17.3 Experimental API

`@ApiStatus.Experimental` は必須機能では原則避ける。

使用する場合は以下を満たすこと。

1. 代替 public stable API がない
2. 機能を切り離せる
3. Plugin Verifier で対象 IDE バージョンを確認する
4. 設計書に理由を記録する

---

## 18. PoC Gate

本格実装前に、以下の技術検証を完了する。**各 Gate は定量的な GO/NO-GO 判定とし、NO-GO の場合は v1.0 の仕様を修正してから開発へ進む。**

### Gate 1: Eligible Page discovery

確認項目:

- Application / Project Settings を EP メタデータのみから列挙できる
- ID / displayName(または key+bundle)/ parent hierarchy を Configurable インスタンス生成なしで取得できる
- **Eligible 率の測定**: 素の IDE(+ 代表的プラグイン数個)の全 Settings ページに対する Eligible Page の比率を記録する

**Reference set(分母)の測定方法**:

Settings Jump 本体は Dynamic を列挙せず、Configurable を生成せず、Internal API も使わないため、「Settings UI に実際に表示される全ページ数」は製品コードの経路では取得できない。分母は Gate 測定専用の別経路で取得する。

- **主手段(案A)**: Starter + Driver でテスト IDE の Settings tree を実際に巡回し、UI Inspector 相当の情報(Configurable ID 等)から reference set を作成する
- **代替(案B)**: PoC 専用 diagnostic code(`src/poc` 等。配布物には絶対に含めない)から IDE 内部の列挙結果を reference として取得する

いずれの場合も、**「Internal API 禁止(17節)は配布 Plugin の runtime code に対する制約」であり、Gate 測定用の使い捨てコードには適用しない**ことを明示する。測定手順は再現可能な形で記録する。

対象例(すべて Eligible であることを確認する):

- Editor > General
- Editor > Code Style
- Version Control > Git
- Build Tools > Gradle
- Plugins
- Keymap
- HTTP Proxy

判定:

- 上記の主要ページが 1 つでも Non-eligible → 原因を分析し、Eligible 条件または企画を再評価
- 日常利用ページの大部分が Non-eligible → **企画そのものを再評価**(実装での救済はしない)

### Gate 2: Dynamic / static カバレッジ

確認項目:

- 静的 EP 宣言のみで index した場合に、Settings UI 表示との差分がどの程度あるか
- 主要ページ(Gate 1 対象例)が dynamic child に該当していないか
- `childrenEPName` 由来ページの出現率(v1.0 対象外の妥当性確認。8.2節)
- index 構築が過剰な class loading を発生させないこと
- IDE 起動時間へ悪影響を与えないこと

判定:

- Dynamic 除外方針(5.2節)のまま実用に足るカバレッジがある → GO
- 日常利用ページの大部分が dynamic → 除外方針を再検討

### Gate 3: Stable Navigation(最重要)

必須条件(すべて満たして GO):

1. `@ApiStatus.Internal` API を使わない
2. stable configurable ID だけで対象を識別できる
3. 同名ページがあっても誤ったページを開かない
4. Application / Project scope の双方で動作する
5. predicate traversal が dynamic child の生成等による異常な副作用を起こさない
6. Settings 通常起動と比較して許容できない追加遅延がない(実測する)
7. 対象 ID が消えている場合 fail closed する(何も開かず、エラーも発生させない)。preflight(10.2節)で防ぎ、`ShowSettingsUtil` の predicate 不一致例外(`ConfigurableVisitor.find ?: error(...)`)を IDE へ漏らさないこと

**この Gate に落ちた場合、Favorite / Slot 実装へ進まない。**

### Gate 4: Third-party plugin

標準的な Configurable EP を使用するプラグインを最低 2 種類確認する。

確認項目:

- ページ検出(Eligible 判定が正しく機能する)
- hierarchy 表示
- 直接 Open
- プラグイン無効化後の安全な処理(`DynamicPluginListener` → invalidate → 利用不可表示)

### Gate 5: Shortcut Slots

確認項目:

- 固定 Action が Settings > Keymap に表示される
- ユーザーが任意キーを設定できる
- Slot から Settings page を開ける
- Slot mapping を変更しても Action ID は不変

### Gate 6: Public API / Plugin Verifier

- Internal API 使用なし
- deprecated-for-removal 使用なし
- Plugin Verifier で対象 IDE range(20節)に重大な compatibility error がない

---

## 19. パフォーマンス

Settings Jump は Settings へのアクセスを高速化するツールであり、起動時に IDE を遅くしてはならない。

### 19.1 方針

- IDE startup critical path で index 構築をしない
- Configurable インスタンスを index 構築で生成しない(9.1節)
- Popup 初回起動時または適切な background timing で index を構築する
- index を再利用し、`DynamicPluginListener` イベント時のみ invalidate する(9.5節)

### 19.2 目標

- Popup 表示は体感上即時
- 数百 Settings page 程度では検索結果更新に待ちを感じさせない
- Search 処理に PSI / VFS scan を使用しない
- Navigation(predicate traversal)の追加遅延は Settings 通常起動と比較して許容範囲(Gate 3 で実測・判定)

---

## 20. 対象環境・ビルド基盤

Plugin ID は Marketplace 公開後に変更できないため、早期に確定する。

```text
Language:              Kotlin
Build:                 Gradle Kotlin DSL
Plugin tooling:        IntelliJ Platform Gradle Plugin 2.x
Baseline IDE:          IntelliJ IDEA 2024.2(sinceBuild = 242)
JDK:                   21 baseline
Plugin ID:             com.github.kanicream.settingsjump
Display Name:          Settings Jump
```

根拠:

- 2024.2+ は IntelliJ Platform Gradle Plugin 2.x / Java 21 世代であり、現行 API 方針(coroutine ベース)に統一できる
- Settings Jump はほぼ Platform API のみに依存するため、baseline を新しめに置くデメリットが小さい
- 2026.2+ の Java 25 環境も baseline 242 のまま管理できる
- Marketplace 上で "Settings Jump" の明確な名前衝突は現時点で確認されていない

untilBuild は原則指定しない(Platform 推奨に従う)。supported range の最終確定は Plugin Verifier の結果を踏まえ v0.5 で行う。

---

## 21. エラーハンドリング

### 21.1 Favorite 対象が消えた・現在の context に存在しない

例:

- Plugin が uninstall / disable された
- IDE upgrade で Configurable ID が変更された
- CONTEXTUAL なページ(`ConfigurableProvider` 由来)が現在の Project では提供されない(4.3節)

処理:

```text
Favorite entry を保持
↓
resolve 失敗
↓
Unavailable として扱う
↓
ユーザーが削除可能
```

自動的に displayName が同じ別ページへ移行しない。ID 不一致時は fail closed とする。

CONTEXTUAL なページの場合、Unavailable は恒久的な破損ではなく「この Project では利用できない」状態であり得る。entry は削除せず保持し、別の Project では通常どおり機能する。UI 上は「この設定はプロジェクトによって利用できない場合がある」ことが分かる表示とする。

### 21.2 Shortcut 対象が消えた

- Settings を開かない
- IDE error を発生させない
- 軽量な通知を出す
- Slot の再設定導線を提供する

### 21.3 PROJECT scope ページを Project なしで開こうとした

- Settings を開かない
- 軽量な通知: "This setting requires an open project"
- Favorite / Slot は壊さない

---

## 22. テスト方針

Settings Jump は設定値を変更しないため、テスト対象を Navigation に限定できる。テストは実行形態別に 4 層に分ける。

### 22.1 Unit Test

- Search normalization / ranking
- Navigation Path 構築
- Eligible 判定ロジック
- Favorite state
- Recent ordering
- Shortcut Slot mapping
- missing Configurable handling
- persistence(schema version 含む)

### 22.2 Integration / API Test

- EP メタデータからの Eligible Page discovery
- Application / Project scope 判定
- Configurable ID resolution

### 22.3 UI Integration Test(Starter + Driver)

IDE 全体を起動する Integration Test は JetBrains が正式に案内する Starter + Driver を使用する。

- Popup open
- Search result select
- Settings dialog open
- 期待した Settings page が選択されていること

### 22.4 Manual Test

- Localization(日本語 UI での検索)
- Plugin install / disable / uninstall
- 複数 Project ウィンドウ
- Project なし(Welcome 画面)での Slot 実行
- IDE restart 後の永続化
- Keymap 変更
- Dark / Light UI

### 22.5 Compatibility Test

Plugin Verifier を CI へ組み込む。対象は baseline 242 以降の supported range(20節)。

---

## 23. セキュリティ / プライバシー

Settings Jump は外部通信を必要としない。

- Telemetry: v1.0 では不要
- External API: なし
- AI API: なし
- Settings value collection: なし
- Project source code collection: なし

保存するのは Settings page ID、Favorite、Recent、Shortcut Slot 等のローカル UI state のみとする。

---

## 24. リリース計画

Settings Jump は機能の依存関係が単純であるため、細かいマイルストーンとバージョンを対応させず、**Phase 0(技術検証)→ v0.5(Public Preview)→ v1.0(Stable)の3段階**とする。

標準機能との差別化(2.2節)を踏まえ、**Search 単体では公開しない**。初公開はコア価値(Shortcut Slots)を含む一通りの機能が揃った v0.5 とする。内部の進行管理はコミット・Issue 単位で行い、中間バージョンは付けない。

### Phase 0 — Technical Validation

**Plugin version は付けない。Marketplace 公開なし。**

`poc/` 等の検証用領域で以下を実施する(製品コードとは分離。Gate 測定用コードの扱いは 17.2節・18節 Gate 1)。

- Gate 1: Eligible Page discovery(Eligible 率の実測)
- Gate 2: Static coverage(dynamic / childrenEPName の出現率)
- Gate 3: Stable Navigation(preflight + predicate navigation の成立性・性能)

完了条件:

> Gate 1〜3 がすべて GO である。

**NO-GO の場合は本実装へ進まない**(設計修正または企画再評価)。

### v0.5 — Public Preview(Marketplace 初公開)

Settings Jump として必要な機能を一通り提供する。

- Settings Page Index(Application / Project 二層)
- Search Popup(Localization 考慮の search index)
- Direct Navigation(preflight + fail closed)
- Favorites(Popup 内キーバインド確定を含む)
- Recent(重複整理・最大件数管理)
- Shortcut Slots 1〜10(Keymap integration / Slot assignment UI。Gate 5)
- Project / Application scope
- Contextual availability / unavailable handling
- Plugin lifecycle 追従(`DynamicPluginListener`)
- Third-party Configurable(Gate 4)
- Persistence(schema version 付き)
- Plugin Verifier CI(Gate 6)
- supported IDE range 確定

### v1.0 — Stable Release

v0.5 の実利用結果をもとに安定化する。**新しい大規模機能は原則追加しない。**

- UX polish / Keyboard navigation
- Search ranking tuning
- Performance tuning
- Compatibility fixes
- Error handling 改善
- Regression tests
- Documentation / README
- Marketplace metadata 仕上げ
- Plugin Verifier clean

---

## 25. v1.0 受け入れ条件

以下をすべて満たすこと。

1. Eligible な主要 Settings ページを検索できる
2. 検索結果から正しい Settings ページを直接開ける(誤ページを開かない)
3. Settings の値を Settings Jump が変更しない
4. Favorite を保存・復元できる
5. Recent を保存・復元できる
6. Shortcut Slot から登録ページを直接開ける
7. 対象ページが存在しない場合も IDE error を発生させない(fail closed)
8. PROJECT scope ページを Project なしで実行した場合に安全に案内する
9. 通常のサードパーティー Configurable(Eligible なもの)を基本的に扱える
10. Internal API 依存を持たない
11. deprecated-for-removal API 依存を持たない
12. Plugin Verifier で supported IDE range に重大な compatibility error がない
13. IDE 起動時間へ目立つ悪影響を与えない
14. 日本語 UI 環境で、ID / plugin ID / canonical group alias 由来の英語 token による検索が機能する(表示名の英語版そのものの検索は保証しない)

---

## 26. 将来候補

v1.0 完了後に需要を見て検討する。

### 26.1 Settings aliases

ユーザー独自の別名を Settings ページへ付ける。

```text
"proxy" -> HTTP Proxy
"go fmt" -> Go > Code Style
```

### 26.2 Favorite groups

```text
Coding / Review / Build / VCS
```

### 26.3 Import / Export

Settings 値ではなく、Settings Jump 自身の Favorite / Slot 設定だけを export する(schema version が前提を提供する)。

### 26.4 Search Everywhere integration

公開・安定 API で十分な UX が実現できる場合のみ検討する。

### 26.5 Dynamic Configurable 対応の拡大

公開 API の拡張や JetBrains Platform の変更に応じて改善する。

### 26.6 Non-eligible ページの部分対応

公開 API の改善により Eligible 条件を満たせるようになった場合のみ拡大する。合成キーによる救済は将来も行わない。

### 26.7 マウス向けエントリポイント

Keyboard-first 原則(7.4節)は維持したまま、キー割り当てを覚えたくないユーザー向けの補助入口として、**メインツールバーまたはステータスバーにアイコンを1つ置き、クリックで Search Popup を開く**方式を検討する(Popup 先頭は Favorites なので、クリック → クリックの2ステップで Favorite に到達できる)。

- Shortcut Slot との併用を前提とする(Slot はキー1発、アイコンはマウス2ステップ)
- Tool Window は採用しない(6.1節の判断を維持。常駐 UI は Settings Jump のライフサイクルに合わない)
- v0.5 には含めず、Public Preview のフィードバックを見て判断する

### 26.8 Canonical English label の併載

非英語 UI で表示名の英語版(localized label + canonical English label の両取得)による検索を提供する。v1.0 では保証範囲外(11.1節)。

---

## 27. 明示的に行わない将来拡張

以下は Settings Jump の責務を逸脱するため、原則として将来も対象外とする。

- Settings 値を直接編集する Quick Toggle 化
- IDE 設定 Profile の一括適用
- 他プラグイン Settings 値の Reflection 操作
- UI component を自動クリックする automation
- 合成キー・推定マッチングによる Non-eligible ページの救済

これらが必要になった場合は Settings Jump へ無理に追加せず、別プロダクトとして評価する。

---

## 28. 設計判断まとめ

| 論点 | 判断 |
|---|---|
| 主目的 | Settings への高速アクセス(Shortcut Slot がコア価値) |
| 対象境界 | Eligible Settings Page のみ(4節) |
| 設定値変更 | 行わない |
| Settings identity | stable Configurable ID のみ。合成キーは作らない |
| ID なしページ | v1.0 対象外(fail closed) |
| 表示名取得 | EP メタデータのみ。インスタンス化して取得しない |
| Eligible 条件の位置づけ | Platform 自体の Configurable 要件より意図的に厳しい |
| Eligible / Available | 分離。CONTEXTUAL(provider または nonDefaultProject)も Favorite 可、開けない時は fail closed |
| AvailabilityKind | UI hint のみ。実判定は preflight が全ページに対して実施 |
| Index 構造 | Application / Project の二層。`forContext(project)` で統合 |
| 階層 | Navigation Path(公開メタデータ由来)。UI ツリーの完全再現は保証しない |
| 親子解決 | explicit parentId → nested EP → top-level group |
| Nested static Configurable | Eligible 対象(Dynamic とは区別) |
| childrenEPName | v1.0 対象外。Gate 2 で出現率のみ測定 |
| Dynamic Settings | v1.0 対象外。カバレッジは Gate 2 で実測 |
| Navigation 実行 | preflight(availability 評価は Open 時のみ)+ try-catch 境界で fail closed |
| 英語検索の保証 | ID / plugin ID / alias 由来 token に限定 |
| Gate 測定 | Starter+Driver で reference set 取得(Internal API 禁止は配布 runtime code のみ) |
| Navigation | `ShowSettingsUtil` public predicate + `ConfigurableWithId`。性能は Gate 3 で実測 |
| PROJECT scope | DataContext の Project。Project なしは fail closed |
| scope UNKNOWN | 廃止(判定不能 = Non-eligible) |
| Shortcut | 固定 Slot 方式(10 Slot) |
| Index invalidation | `DynamicPluginListener` + lazy rebuild |
| Threading | BGT coroutine。ReadAction は必要箇所のみ |
| 永続化 | `PersistentStateComponent` + schemaVersion |
| 対象環境 | 2024.2+(sinceBuild 242)/ Kotlin / Gradle Plugin 2.x / JDK 21 |
| Plugin ID | com.github.kanicream.settingsjump |
| リリース段階 | Phase 0(version なし)→ v0.5 Public Preview → v1.0 Stable の3段階のみ |
| UI テスト | Starter + Driver |
| Internal API | 原則禁止 |
| Swing UI 解析 | 禁止 |
| AI / 外部通信 | なし |

Settings Jump の価値は、Settings を再実装することでも、全ページに対応することでもない。

**「公開 API と安定した識別子で安全に扱えるページに限定し、そこへ到達するまでの摩擦を確実に取り除く」**ことをプロダクトの中心原則とする。
