package org.docksidestage.handson.exercise;



import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Resource;

import org.dbflute.cbean.result.ListResultBean;
import org.dbflute.optional.OptionalEntity;
import org.docksidestage.handson.dbflute.exbhv.MemberBhv;
import org.docksidestage.handson.dbflute.exbhv.MemberSecurityBhv;
import org.docksidestage.handson.dbflute.exentity.Member;
import org.docksidestage.handson.dbflute.exentity.MemberSecurity;
import org.docksidestage.handson.dbflute.exentity.MemberStatus;
import org.docksidestage.handson.unit.UnitContainerTestCase;
import org.docksidestage.handson.dbflute.exbhv.PurchaseBhv;
import org.docksidestage.handson.dbflute.exentity.Purchase;

/**
 * @author harukaedo
 */
public class HandsOn03Test extends UnitContainerTestCase {

    @Resource
    private MemberBhv memberBhv;
    @Resource
    private MemberSecurityBhv memberSecurityBhv;
    @Resource
    private PurchaseBhv purchaseBhv;

    /*
    Silverストレッチ①
    会員名称がSで始まる1968年1月1日以前に生まれた会員を検索
    - 会員ステータスも取得する
    - 生年月日の昇順で並べる
    - 会員が1968/01/01以前であることをアサート(※"以前" の解釈は、"その日ぴったりも含む" で。)
     */
    public void test_memberNameStartsWithSAndBirthdateBefore19680101() throws Exception {
        // Arrange
        //会員の検索条件であり、会員名称と生年月日を指定するための変数を用意しておく
        String prefix = "S";
        // #1on1: 1968/1/1 自体が birthdate そのものか？話 (2026/05/29)
        // 概念力の話。遵守性は高い方が良いですよ。
        //0601修正メモ
        //検索の起点となる生年月日なので命名を少し変える
        //birthdateからtargetBirthdateに変える。targetBirthdateの方が、検索の起点となる日付であることがわかりやすいと思う。
        LocalDate targetBirthdate = LocalDate.of(1968, 1, 1);
        //Dateがたくさんある！😭
        //java.sql.Dateかと思ったらコンパイルエラーになった
        // #1on1: java.util.Date, java.sql.Date とパッケージ違いの同名クラス (2026/05/29)
        // 補完ノイズ。ほとんど util.Date しか使わないのに。
        // packageでユニークにしても、クラス名である程度のユニーク性が欲しい話。
        // DBのテーブル名とカラム名での例のお話。MEMBER_NAME? or NAME?
        //
        // #1on1: 一方で、java.util.Dateは古いクラスで、今はLocalDateを使っている。
        // これは、java.util.Date系のクラスが残念な実装になっててみんなイヤだったので...
        // 新しく作り直したLocalDateが登場。でも古いクラスは消せないので残っている。
        // (Javaは(できるだけ)互換性をキープすることをポリシーとしている)
        // (Javaのオフィシャル自体もそういう冒険をしちゃうもの)

        // Act
        //&検索なので、orはつけない
        //複数検索なのでListで取得
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            // done edo setupSelectはselect句のメソッドなので、一番上に定義したい by jflute (2026/05/29)
            // これはDBFluteの提案的慣習で、別に必須ではないけど、みんながそうやれば可読性が良くなる。
            //会員ステータスも取得する
            //0601修正メモ:setupSelectは、select句のためのjoinなので、基本的には一番上に定義する
            cb.setupSelect_MemberStatus();
            //セクション２でやったように曖昧検索
            cb.query().setMemberName_LikeSearch(prefix, op -> op.likePrefix());
            //生年月日が指定した日付よりも下であることを条件に加える
            cb.query().setBirthdate_LessEqual(targetBirthdate);
            cb.query().addOrderBy_Birthdate_Asc();
        });

        // Assert
        assertFalse(memberList.isEmpty());
        for (Member member : memberList) {
        	// done edo 複数 get...() している箇所を変数化してみましょう。変数名いい感じに by jflute (2026/05/29)
            //0601修正メモ
            //memberNameとmemberBirthdateという変数を用意してlogやアサートで使うようにした
        	// #1on1: assertを目視確認するために、log()を自由に出しても良い (2026/05/29)
            String memberName = member.getMemberName();
            LocalDate memberBirthdate = member.getBirthdate();
        	log(memberName, memberBirthdate);

            assertTrue(memberName.startsWith(prefix));
            //該当日も含めるので0以下であることをアサート
            assertTrue(memberBirthdate.compareTo(targetBirthdate) <= 0);
            // done edo getMemberStatus()はJavaDoc見るとnullを戻さないので意味のないアサートになってる by jflute (2026/05/29)
            //0601修正メモ
            //assertNotNullは常に成功してしまうので、assertTrueの方に変えて、OptionalEntityがemptyじゃないことをチェックするようにした
            // assertNotNull(member.getMemberStatus());
            // 「会員ステータスを取得したか？」の判定手段がnullではないということ。
            // OptionalEntity がemptyか？emptyじゃないか？で判定しないといけない。
            // #1on1: UnitTestのアサートを書く時の慣習、いっかい落としてアサート自体を確認しましょう (2026/05/29)
			//assertNotNull(member.getMemberStatus());
            assertTrue(member.getMemberStatus().isPresent());
        }
    }

    /*
    Silverストレッチ②
    会員ステータスと会員セキュリティ情報も取得して会員を検索
    - 若い順で並べる。生年月日がない人は会員IDの昇順で並ぶようにする
    - 会員ステータスと会員セキュリティ情報が存在することをアサート
    ※カージナリティを意識しましょう
     */
    //若い順に並べた会員と、会員IDない人はどっちを先に並べるのか
    //特段定義されていないが、生年月日がNullという扱いにして最後に持ってくるようにする
    //イメージ→　年齢層（低）→ 年齢層（高）→　生年月日がない人
    public void test_memberWithMemberStatusAndMemberSecurity() throws Exception {
        // Arrange
        //特に定義は必要なさそう

        // Act
        //複数検索なのでListで取得
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
        	// TODO jflute 次回1on1, カージナリティ (2026/05/29)
            //会員のステータスとセキュリティ情報を取得する
            cb.setupSelect_MemberStatus();
            cb.setupSelect_MemberSecurityAsOne();
            // #1on1: 第一ソートキーだけでは、ユニークに並ばない話 (2026/05/29)
            // 同じ生年月日の会員同士は、どう並べればいいの？問題。
            // ユニークにならないと何が良くないか？ → 検索のたびに順序が変わる可能性
            // 画面としては、データが変わってないのに見た目が変わるってあまりUI的に良くない。
            // だからこそ第二ソートキーがある。(固定化のためだけの最後PKソートってこともある)
            //若い順＝生年月日新しい降順で並べる.最後にnullを持ってくる
            cb.query().addOrderBy_Birthdate_Desc().withNullsLast();
            //生年月日がない人は会員IDの昇順で並ぶようにする
            cb.query().addOrderBy_MemberId_Asc();
        });

        // Assert
        assertFalse(memberList.isEmpty());
        for (Member member : memberList) {
        	// done edo assertになってない (null検査ではない) by jflute (2026/05/29)
            //0601修正メモ
            //assertNotNullは常に成功してしまうので、assertTrueの方に変えて、OptionalEntityがemptyじゃないことをチェックするようにした
            // assertNotNull(member.getMemberStatus());
            // assertNotNull(member.getMemberSecurityAsOne());
            assertTrue(member.getMemberStatus().isPresent());
            assertTrue(member.getMemberSecurityAsOne().isPresent());
        }
    }

    /*
    Silverストレッチ③
    会員セキュリティ情報のリマインダ質問で2という文字が含まれている会員を検索
    - 会員セキュリティ情報のデータは不要
    - (Actでの検索は本番でも実行されることを想定し、テスト都合でパフォーマンス劣化させないこと)
    - リマインダ質問に2が含まれていることをアサート
    - アサートするために別途検索処理を入れても誰も文句は言わない
     */
    public void test_reminderQuestion_contains_2() throws Exception {
        // Arrange
        //リマインダ質問に2が含まれていることをアサートするための変数を用意しておく
        String containsStr = "2";

        // Act
        //複数検索なのでListで取得
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
        	// #1on1: where句のためのjoin (無駄なデータは取ってこない) (2026/05/29)
        	// joinというのは、基本的には何かの目的を達成するための手段。
        	// select句のためのjoin, where句のためのjoin, order by句のためのjoin
        	// joinだけして嬉しいことはそんなにない。(文法的には色々できるけど基本的には)
        	//
            //会員セキュリティ情報のリマインダ質問で2という文字が含まれている会員を検索
            cb.query().queryMemberSecurityAsOne().setReminderQuestion_LikeSearch(containsStr, op -> op.likeContain());
        });

        // Assert
        //一回りマインドの質問が含まれているものを全件持ってきて、そこからフィルタリングして２を見つけたい
        assertFalse(memberList.isEmpty());
        for (Member member : memberList) {
        	// done edo トレーニング: MEMBERをもっかい検索する必要はなく... by jflute (2026/05/29)
        	// 会員セキュリティだけを検索するようにしてみましょう。memberSecurityBhv を連れてきて...
            //0601修正メモ
            //memberBhvを使用するのではなく、memberSecurityBhvを使用して、会員セキュリティ情報だけを検索するようにした
            //会員セキュリティ情報のリマインダ質問で2という文字が含まれている会員を検索
            //behaviorは、memberBhrしか使えないと勘違いしていた
            //色々あるんだなと勉強になった
            //会員セキュリティ情報のデータは不要なので、別途検索処理を入れる
            // Member memberWithSecurity = memberBhv.selectEntityWithDeletedCheck(cb -> {
            //     cb.query().setMemberId_Equal(member.getMemberId());
            //     cb.setupSelect_MemberSecurityAsOne();
            // });
            MemberSecurity memberSecurity = memberSecurityBhv.selectEntityWithDeletedCheck(cb -> {
                cb.query().setMemberId_Equal(member.getMemberId());
            });
            //別途処理をした上でアサートする
            // String reminderQuestion = memberWithSecurity.getMemberSecurityAsOne().get().getReminderQuestion();
            // assertTrue(reminderQuestion.contains(containsStr));
            String reminderQuestion = memberSecurity.getReminderQuestion();
            assertTrue(reminderQuestion.contains(containsStr));
        }
    }

    /*
    Goldストレッチ④
    会員ステータスの表示順カラムで会員を並べて検索
    - 会員ステータスの "表示順" カラムの昇順で並べる
    - 会員ステータスのデータ自体は要らない
    - その次には、会員の会員IDの降順で並べる
    - 会員ステータスのデータが取れていないことをアサート
    - 会員が会員ステータスごとに固まって並んでいることをアサート (順序は問わない)
     */
    public void test_orderByMemberStatusDisplayOrder() throws Exception {
        // Arrange
        //並び替えだけなので、特に定義は必要なさそう

        // Act
        //複数検索なのでListで取得
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            //会員ステータスの "表示順" カラムの昇順で並べる
            //会員ステータスの "表示順" カラムの昇順で並べる
        	//会員ステータスの "表示順" カラムの昇順で並べる
            cb.query().queryMemberStatus().addOrderBy_DisplayOrder_Asc();
            //会員の会員IDの降順で並べる
            cb.query().addOrderBy_MemberId_Desc();
        });

        // Assert
        //空確認

        assertFalse(memberList.isEmpty());
        //固まって並ぶ　＝　同じステータスが連続したブロックで現れる。一度途切れたら違うステータスになっている
        //(例)　FML FML WDL WDL PRVの場合は、固まっているものをアサートしてると言える
        //(例)　FML WDL FMLの場合は、固まっているものをアサートしてると言えない
        //内容を細かく見なくても、同じものがブロックになっているかだけを見れば良い
        //→ステータスが切り替わるところを回数としてカウント＝ステータス種類数となる

        //出てきた会員ステータスの種類を記録
        Set<String> memberStatusSet = new HashSet<>();
        //ステータスの切り替わり回数を数えるための変数
        int switchCount = 0;
        //前の会員ステータスを覚えておくための変数
        String previousStatusCode = null;
        for (Member member : memberList){
            //会員ステータスのデータが取れていないことをアサート
            //取れてないことを確認したいので、assertFalseにする
            assertFalse(member.getMemberStatus().isPresent());

            //会員ステータスのコードを取得
            String statusCode = member.getMemberStatusCode();
            // TODO edo previousStatusCodeの更新と並べて、事務手続きは寄せちゃった方が by jflute (2026/06/05)
            //出てきた会員ステータスの種類を記録
            memberStatusSet.add(statusCode);
            //ステータスが切り替わるところを回数としてカウント＝ステータス種類数となる
            if (!statusCode.equals(previousStatusCode)) {
                switchCount++;
                // TODO edo previousのニュアンスを1ループ前という解釈にして、ifの外に出した方が読み手は楽 by jflute (2026/06/05)
                previousStatusCode = statusCode;
            }
        }
        // 会員が会員ステータスごとに固まって並んでいることをアサート
        // 切り替わった数＝ステータスの種類数であることをアサートすれば、固まっていることの確認になる
        assertEquals(memberStatusSet.size(), switchCount);

        // #1on1: 別の考え方 (2026/06/05)
        // 切り替わるタイミングで、今まですでに登場してきたステータスが新しく登場したらOUT
        // A A B B C C
        // A A B B A C C
        /*
if (!statusCode.equals(previousStatusCode)) {
	assertFalse(memberStatusSet.contains(statusCode));
         */
        // 特徴の差はある。早いチェック、変数が少なくなる。
        // 逆に、どのくらい固まってないのか？を知るには、えどさんのやり方じゃないと。
        // プログラミング的発想か？データ分析的発想か？
        //
        // ぼくらは例外です。
    }

    /*
    Goldストレッチ⑤
    生年月日が存在する会員の購入を検索
    - 会員名称と会員ステータス名称と商品名を取得する(ログ出力)
    - 購入日時の降順、購入価格の降順、商品IDの昇順、会員IDの昇順で並べる
    - OrderBy dがたくさん追加されていることをログで目視確認すること
    - 購入に紐づく会員の生年月日が存在することをアサート
    */
   //
   public void test_existsBirthdateMemberPurchase() throws Exception {
    // Arrange
    // 特定のものでもないので、特に定義は必要なさそう

    // Act
    // 複数指定になるので、Listで取得する
    ListResultBean<Purchase> purchaseList = purchaseBhv.selectList(cb -> {
        // select句たち
        // 会員の名称・ステータス名称を取得するためにセットする(select句たち)
        cb.setupSelect_Member().withMemberStatus();
        // 購入した商品名も取得したいので、セットする
        cb.setupSelect_Product();
        // where句たち
        // 会員の生年月日が存在することを条件に加える
        cb.query().queryMember().setBirthdate_IsNotNull();
        // 購入日時の降順、購入価格の降順、商品IDの昇s順、会員IDの昇順で並べる
        cb.query().addOrderBy_PurchaseDatetime_Desc();//購入日時の降順
        cb.query().addOrderBy_PurchasePrice_Desc();//購入価格の降順
        cb.query().addOrderBy_ProductId_Asc();//商品IDの昇順
        cb.query().addOrderBy_MemberId_Asc();//会員IDの昇順
    });
	// #1on1: haru質問: setupSelect_...()とquery...()をテーブル同士でくっつけるか？ (2026/08/04)
    //
    // Effective ConditionBean | DBFlute
    // https://dbflute.seasar.org/ja/manual/function/ormapper/conditionbean/effective.html#implorder
    //
    // 実装順序は、データの取得、絞り込み、並び替え
    // DBFluteの決まり(提案)としては、もう目的ごとに書くようにしている。
    //
    // setupSelect_とquery...は、目的として全然別物で、
    // どちらか片方だけになることもある。
    // order byのことも考えると、テーブル単位で合わせることに無理が出てくるので、
    // 目的ごとに集めるのが自然で、SQLのイメージにも合う。
    //
    // 一方で、SpecifyColumnはSetupSelectに合わせる。
    // それは、SpecifyColumnはSetupSelectに属する機能だから。
    // select句に関する制御という意味で目的が同じだから。

    // #1on1: jflute質問: Slackの隣の絵文字はどうやってるのか？ (2026/08/04)
    // statusで絵文字使えること教えてもらった。

    // Assert
    // 空チェック
    assertFalse(purchaseList.isEmpty());
    for (Purchase purchase : purchaseList) {
        // 会員名称と会員ステータス名称と商品名を取得する(ログ出力)
        String memberName = purchase.getMember().get().getMemberName();
        String memberStatusName = purchase.getMember().get().getMemberStatus().get().getMemberStatusName();
        String productName = purchase.getProduct().get().getProductName();
        log(memberName, memberStatusName, productName);

        // 購入に紐づく会員の生年月日が存在することをアサート
        assertTrue(purchase.getMember().get().getBirthdate() != null);
    }
   }
}
