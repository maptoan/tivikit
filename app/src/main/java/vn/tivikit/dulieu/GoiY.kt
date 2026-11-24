package vn.tivikit.dulieu

import android.content.Context
import org.json.JSONObject
import vn.tivikit.t
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * Gợi ý app cho tivi (25/10/2026, mở rộng cùng ngày: app cộng đồng trên GitHub như SmartTube, app chính thức trên CH
 * Play). Hai kiểu nguồn:
 * - App mã nguồn mở — chính thức (VLC, Kodi…) hoặc không chính thức do cộng đồng làm (SmartTube, TizenTube,
 *   SmartTwitchTV…): tải bản mới nhất từ chính nơi tác giả phát hành (GitHub Releases, get.videolan.org,
 *   mirrors.kodi.tv), chọn file theo ABI của tivi.
 * - App chính thức chỉ có trên CH Play (TV360, Netflix…): mở trang của nó trên CH Play của tivi, cài bằng tài khoản
 *   Google của tivi.
 * Không đưa app bẻ khoá, bản sửa của app trả phí, hay app sống nhờ nguồn phim / IPTV lậu (CloudStream, Streamflix,
 * app torrent / debrid…), kể cả khi chúng có mã nguồn trên GitHub.
 */
object GoiY {
    sealed class Nguon {
        /**
         * releases/latest của [repo]; file .apk có tên khớp [loc] (nhiều file thì chọn theo ABI). [tenAbi]: tên ABI kiểu
         * riêng của dự án đó (LocalSend ghi "arm64v8"), khoá là ABI đã chuẩn hoá (chữ thường, "_" → "-").
         */
        class GitHub(val repo: String, val loc: Regex, val tenAbi: Map<String, String> = emptyMap()) : Nguon()
        /** Trang liệt kê thư mục theo ABI; file khớp [loc], chọn bản số cao nhất (bỏ rc / beta nhờ [loc]). */
        class ThuMuc(val theoAbi: Map<String, String>, val loc: Regex) : Nguon()
        /** App chính thức chỉ có trên CH Play: mở trang của gói đầu tiên trong App.goi trên CH Play của tivi. */
        object CHPlay : Nguon()
    }

    enum class Nhom(private val vi: String, private val en: String) {
        VIDEO("YouTube, xem video, IPTV", "YouTube, video & IPTV"),
        VIET("Truyền hình, phim Việt Nam", "Vietnamese TV & films"),
        ANIME("Anime, phim châu Á (bản quyền)", "Anime & Asian films (licensed)"),
        QUOC_TE("Phim, nhạc quốc tế", "International films & music"),
        MAY_CHU("Phim từ máy chủ trong nhà", "Films from a home server"),
        GAME("Chơi game", "Games"),
        LAUNCHER("Launcher (màn Home)", "Launchers (Home screens)"),
        TIEN_ICH("Tiện ích", "Utilities");

        val ten: String get() = t(vi, en)
    }

    /**
     * Một app gợi ý. [goi]: tên gói của app khi cài lên tivi (để biết tivi đã có chưa); với CH Play, gói đầu tiên là
     * trang mở. [khongChinhThuc]: app cộng đồng làm cho dịch vụ của hãng khác (SmartTube cho YouTube…).
     */
    class App(
        val id: String, val ten: String, private val vi: String, private val en: String, val nguon: Nguon, val nhom: Nhom,
        val goi: List<String>, val khongChinhThuc: Boolean = false,
    ) {
        val moTa: String get() = t(vi, en)
        val laCHPlay: Boolean get() = nguon is Nguon.CHPlay
    }

    private fun play(id: String, ten: String, vi: String, en: String, nhom: Nhom, goi: String) = App(id, ten, vi, en, Nguon.CHPlay, nhom, listOf(goi))

    /** ABI thường gặp trong tên file (dùng chung cho các dự án ghi theo kiểu Android). */
    private const val ABI_ANDROID = "arm64-v8a|armeabi-v7a|x86_64|x86|universal"

    val DS = listOf(
        // ---------- YouTube, xem video, IPTV ----------
        App("smarttube", "SmartTube", "YouTube cho tivi không quảng cáo, tự bỏ qua đoạn tài trợ (SponsorBlock), phát tới 8K, HDR. Không chính thức, mã nguồn mở.",
            "YouTube for TVs without ads, skips sponsor segments (SponsorBlock), up to 8K and HDR. Unofficial, open source.",
            Nguon.GitHub("yuliskov/SmartTube", Regex("""^SmartTube_stable_[\d.]+_(arm64-v8a|armeabi-v7a|x86|universal)\.apk$""")),
            Nhom.VIDEO, listOf("org.smarttube.stable"), khongChinhThuc = true),
        App("tizentube", "TizenTube", "YouTube với giao diện y như app gốc trên tivi, chặn quảng cáo, có SponsorBlock, chỉnh tốc độ. Không chính thức, mã nguồn mở.",
            "YouTube with the stock TV app's look, ad-free, with SponsorBlock and speed control. Unofficial, open source.",
            Nguon.GitHub("reisxd/TizenTubeCobalt", Regex("""^cobalt-(arm|arm64)\.apk$"""), mapOf("arm64-v8a" to "-arm64.", "armeabi-v7a" to "-arm.")),
            Nhom.VIDEO, listOf("io.gh.reisxd.tizentube.cobalt"), khongChinhThuc = true),
        App("smarttwitch", "SmartTwitchTV", "Xem Twitch trên tivi: chat, nhiều luồng cùng lúc, chọn chất lượng. Không chính thức, mã nguồn mở.",
            "Twitch on the TV: chat, several streams at once, quality picker. Unofficial, open source.",
            Nguon.GitHub("fgl27/SmartTwitchTV", Regex("""^SmartTV_twitch_[\d_]+\.apk$""")), Nhom.VIDEO, listOf("com.fgl27.twitch"), khongChinhThuc = true),
        App("justplayer", "Just Player", "Trình phát video gọn nhẹ: mở file từ USB, ổ mạng, link; phụ đề, HDR, chỉnh tốc độ.",
            "A light video player: files from USB, network drives or links; subtitles, HDR, speed control.",
            Nguon.GitHub("moneytoo/Player", Regex("""^Just\.Player\.v[\d.]+\.apk$""")), Nhom.VIDEO, listOf("com.brouken.player")),
        App("vlc", "VLC", "Xem mọi loại video / nhạc, mở file từ USB và mạng (VideoLAN).",
            "Plays any video / music, opens files from USB and the network (VideoLAN).",
            Nguon.ThuMuc(mapOf("arm64-v8a" to "https://get.videolan.org/vlc-android/last/", "armeabi-v7a" to "https://get.videolan.org/vlc-android/last/",
                "x86_64" to "https://get.videolan.org/vlc-android/last/", "x86" to "https://get.videolan.org/vlc-android/last/"),
                Regex("""VLC-Android-\d+(\.\d+)*-(arm64-v8a|armeabi-v7a|x86_64|x86)\.apk""")),
            Nhom.VIDEO, listOf("org.videolan.vlc")),
        App("kodi", "Kodi", "Trung tâm giải trí: phim, nhạc, ảnh từ ổ cứng và mạng (Kodi Foundation).",
            "Media center for films, music and photos from disks and the network (Kodi Foundation).",
            Nguon.ThuMuc(mapOf("arm64-v8a" to "https://mirrors.kodi.tv/releases/android/arm64-v8a/", "armeabi-v7a" to "https://mirrors.kodi.tv/releases/android/arm/"),
                Regex("""kodi-\d+(\.\d+)+-[A-Za-z]+-(arm64-v8a|armeabi-v7a)\.apk""")),
            Nhom.VIDEO, listOf("org.xbmc.kodi")),
        App("nova", "Nova Video Player", "Xem phim từ USB, ổ mạng (SMB, NAS): tự tìm poster, thông tin phim và phụ đề.",
            "Films from USB or network drives (SMB, NAS), with posters, film info and subtitles fetched for you.",
            Nguon.GitHub("nova-video-player/aos-AVP", Regex("""^org\.courville\.nova-.+-($ABI_ANDROID)-release\.apk$""")), Nhom.VIDEO, listOf("org.courville.nova")),
        App("mpv", "mpv", "Trình phát mpv: nhẹ, chơi gần như mọi định dạng, chỉnh hình rất sâu.", "The mpv player: light, plays almost anything, deep picture settings.",
            Nguon.GitHub("mpv-android/mpv-android", Regex("""^app-default-($ABI_ANDROID)-release\.apk$""")), Nhom.VIDEO, listOf("is.xyz.mpv")),
        // IPTV (1/11/2026): chỉ là trình phát, người dùng tự thêm danh sách kênh của mình; cả hai không kèm kênh nào. Bỏ bản
        // "lite" của M3UAndroid và OwnTV.apk (trùng bản có số, không đọc được phiên bản từ tên).
        App("m3uandroid", "M3UAndroid", "Xem IPTV bằng danh sách kênh của anh (link M3U): gọn, không quảng cáo, dùng được bằng remote. Mã nguồn mở.",
            "Watch IPTV from your own playlist (an M3U link): clean, ad-free, works with the remote. Open source.",
            Nguon.GitHub("oxyroid/M3UAndroid", Regex("""^[\d.]+_($ABI_ANDROID)\.apk$""")), Nhom.VIDEO, listOf("com.m3u.smartphone")),
        App("owntv", "OwnTV", "Trình phát IPTV làm riêng cho tivi: danh sách M3U, tài khoản Xtream hay Stalker của anh, lịch phát sóng (EPG), hai trình phát mpv và ExoPlayer. Không kèm sẵn kênh nào. Mã nguồn mở.",
            "An IPTV player made for TVs: your own M3U playlist, Xtream login or Stalker portal, a TV guide (EPG), mpv and ExoPlayer engines. Ships no channels. Open source.",
            Nguon.GitHub("ahXN00/OwnTV", Regex("""^OwnTV-(x86_64-)?v[\d.]+\.apk$""")), Nhom.VIDEO, listOf("tv.own.owntv")),

        // ---------- Truyền hình, phim Việt Nam (CH Play) ----------
        play("tv360", "TV360", "Truyền hình Viettel: VTV, HTV, kênh địa phương, phim, thể thao (dùng với Kênh nhanh).",
            "Viettel TV: VTV, HTV, local channels, films, sport (works with Quick channel).", Nhom.VIET, "com.viettel.tv360.tv"),
        play("fptplay", "FPT Play", "Truyền hình, phim, thể thao của FPT.", "FPT's TV channels, films and sport.", Nhom.VIET, "net.fptplay.ottbox"),
        play("vieon", "VieON", "Phim bộ, show, truyền hình của Vie Channel.", "Series, shows and TV from Vie Channel.", Nhom.VIET, "com.vieon.tv"),
        // Bản cho tivi (vn.vtv.vtvgotv, "VTVgo … cho TV"); vn.vtv.vtvgo là bản điện thoại. Ô kênh trên Home sạch mở bản này.
        play("vtvgo", "VTVgo", "Xem trực tiếp các kênh VTV miễn phí (dùng với ô kênh trên Home sạch).", "Watch the VTV channels live for free (works with Home channel tiles).", Nhom.VIET, "vn.vtv.vtvgotv"),
        play("kplus", "K+", "Bóng đá, phim, thể thao của K+ (cần gói K+).", "K+ football, films and sport (needs a K+ plan).", Nhom.VIET, "com.irdeto.kplus"),

        // ---------- Anime, phim châu Á có bản quyền (CH Play) ----------
        play("bilibili", "Bilibili", "Anime có bản quyền, phụ đề Việt; nhiều tập xem miễn phí.", "Licensed anime with Vietnamese subtitles; many episodes free.", Nhom.ANIME, "com.bstar.intl.tv"),
        play("iqiyi", "iQIYI", "Phim Trung, Hàn, anime có phụ đề Việt; nhiều phim miễn phí.", "Chinese and Korean dramas and anime with Vietnamese subtitles; much of it free.", Nhom.ANIME, "com.iqiyi.i18n.tv"),
        play("crunchyroll", "Crunchyroll", "Kho anime lớn, tập mới chiếu gần như cùng lúc với Nhật (cần gói Crunchyroll).",
            "A big anime library, new episodes close to their Japan airing (needs a Crunchyroll plan).", Nhom.ANIME, "com.crunchyroll.crunchyroid"),

        // ---------- Phim, nhạc quốc tế (CH Play) ----------
        play("netflix", "Netflix", "Phim, phim bộ Netflix.", "Netflix films and series.", Nhom.QUOC_TE, "com.netflix.ninja"),
        play("disney", "Disney+", "Phim Disney, Marvel, Pixar, Star Wars.", "Disney, Marvel, Pixar and Star Wars.", Nhom.QUOC_TE, "com.disney.disneyplus"),
        play("prime", "Prime Video", "Phim, phim bộ của Amazon.", "Amazon's films and series.", Nhom.QUOC_TE, "com.amazon.amazonvideo.livingroom"),
        play("appletv", "Apple TV", "Phim, phim bộ Apple TV+.", "Apple TV+ films and series.", Nhom.QUOC_TE, "com.apple.atve.androidtv.appletv"),
        play("spotify", "Spotify", "Nghe nhạc, podcast.", "Music and podcasts.", Nhom.QUOC_TE, "com.spotify.tv.android"),
        play("ytmusic", "YouTube Music", "Nghe nhạc YouTube trên tivi.", "YouTube music on the TV.", Nhom.QUOC_TE, "com.google.android.youtube.tvmusic"),

        // ---------- Phim từ máy chủ trong nhà ----------
        App("jellyfin", "Jellyfin", "Xem phim từ máy chủ Jellyfin trong nhà.", "Watch films from a Jellyfin server at home.",
            Nguon.GitHub("jellyfin/jellyfin-androidtv", Regex("""-release\.apk$""")), Nhom.MAY_CHU, listOf("org.jellyfin.androidtv")),
        play("plex", "Plex", "Xem phim từ máy chủ Plex trong nhà, kèm kênh miễn phí của Plex.", "Films from a Plex server at home, plus Plex's free channels.", Nhom.MAY_CHU, "com.plexapp.android"),
        play("emby", "Emby", "Xem phim từ máy chủ Emby trong nhà.", "Films from an Emby server at home.", Nhom.MAY_CHU, "tv.emby.embyatv"),

        // ---------- Chơi game ----------
        App("moonlight", "Moonlight", "Chơi game PC trên tivi qua mạng nhà (GameStream / Sunshine).", "Play PC games on the TV over your home network (GameStream / Sunshine).",
            Nguon.GitHub("moonlight-stream/moonlight-android", Regex("""^app-nonRoot-release\.apk$""")), Nhom.GAME, listOf("com.limelight")),
        play("steamlink", "Steam Link", "Chơi game Steam từ máy tính trong nhà.", "Play Steam games from a PC at home.", Nhom.GAME, "com.valvesoftware.steamlink"),
        App("lemuroid", "Lemuroid", "Giả lập máy chơi game cũ (NES, SNES, GBA, PS1…), chơi bằng tay cầm. Game anh tự chép vào.",
            "Emulates classic consoles (NES, SNES, GBA, PS1…) with a gamepad. You bring your own games.",
            Nguon.GitHub("Swordfish90/Lemuroid", Regex("""^lemuroid-app-free-dynamic-release\.apk$""")), Nhom.GAME, listOf("com.swordfish.lemuroid")),
        play("retroarch", "RetroArch", "Giả lập nhiều đời máy chơi game, nhiều tuỳ chỉnh.", "Emulates many consoles, with deep settings.", Nhom.GAME, "com.retroarch"),
        play("ppsspp", "PPSSPP", "Giả lập máy PSP.", "A PSP emulator.", Nhom.GAME, "org.ppsspp.ppsspp"),

        // ---------- Launcher (27/10/2026, Quản lý launcher giai đoạn 3; tên gói đã thử có trên CH Play) ----------
        // Chọn làm Home ở màn Quản lý launcher. Wolf Launcher không còn trên CH Play, FLauncher chỉ phát hành trên GitLab và
        // CH Play: dùng CH Play. Không đưa bản Premium bẻ khoá của Projectivy.
        play("projectivy", "Projectivy Launcher", "Launcher đẹp, tuỳ biến sâu: hàng app, kênh, ảnh nền động, phím tắt remote. Tự thay được Home của hãng (\"Ghi đè trình khởi chạy hiện tại\"); một số tính năng trả phí.",
            "A good-looking, deeply customisable launcher: app rows, channels, live wallpapers, remote shortcuts. Can take over the maker's Home (\"Override current launcher\"); some features are paid.", Nhom.LAUNCHER, "com.spocky.projengmenu"),
        play("flauncher", "FLauncher", "Launcher mã nguồn mở gọn nhẹ: hàng app tự sắp, ảnh nền riêng, không quảng cáo.",
            "A light open-source launcher: app rows you arrange, your own wallpaper, no ads.", Nhom.LAUNCHER, "me.efesser.flauncher"),
        play("atvlauncher", "ATV Launcher Pro", "Launcher dạng lưới ô, gắn được widget đồng hồ, thời tiết; trả phí.",
            "A tile-grid launcher with clock and weather widgets; paid.", Nhom.LAUNCHER, "ca.dstudio.atvlauncher.pro"),
        play("sideload", "Sideload Launcher", "Mở mọi app trên tivi, cả app cài ngoài không hiện trên Home (Chainfire).",
            "Opens every app on the TV, including sideloaded ones the Home doesn't show (Chainfire).", Nhom.LAUNCHER, "eu.chainfire.tv.sideloadlauncher"),

        // ---------- Tiện ích ----------
        play("airscreen", "AirScreen", "Nhận AirPlay / Cast: chiếu màn hình, video từ iPhone, iPad, Mac lên tivi.",
            "Receives AirPlay / Cast: mirror the screen or videos from an iPhone, iPad or Mac.", Nhom.TIEN_ICH, "com.ionitech.airscreen"),
        App("localsend", "LocalSend", "Gửi file qua lại giữa tivi, điện thoại, máy tính trong nhà, như AirDrop.",
            "Send files between the TV, phones and computers at home, like AirDrop.",
            Nguon.GitHub("localsend/localsend", Regex("""^LocalSend-[\d.]+-android-(arm64v8|arm32v7|x64)\.apk$"""),
                mapOf("arm64-v8a" to "arm64v8", "armeabi-v7a" to "arm32v7", "x86-64" to "x64")),
            Nhom.TIEN_ICH, listOf("org.localsend.localsend_app")),
        App("aerialviews", "Aerial Views", "Màn hình chờ cảnh quay từ trên cao như Apple TV. Cài xong chọn nó trong Cài đặt → Màn hình chờ.",
            "Apple TV-style aerial screensaver. After installing, pick it in Settings → Screensaver.",
            Nguon.GitHub("theothernt/AerialViews", Regex("""^aerial-views-[\d.]+\.apk$""")), Nhom.TIEN_ICH, listOf("com.neilturner.aerialviews")),
        App("flicky", "Flicky", "Kho app mã nguồn mở F-Droid, giao diện dùng bằng remote.", "The F-Droid open-source app store with a remote-friendly UI.",
            Nguon.GitHub("mlm-games/flicky", Regex("""^flicky-[\d.]+-($ABI_ANDROID)\.apk$""")), Nhom.TIEN_ICH, listOf("app.flicky")),
        App("fluffy", "Fluffy", "Quản lý file cho tivi: USB, nén / giải nén, mở ảnh và video.", "A file manager for TVs: USB, zip / unzip, open photos and videos.",
            Nguon.GitHub("mlm-games/Fluffy", Regex("""^fluffy-[\d.]+-($ABI_ANDROID)\.apk$""")), Nhom.TIEN_ICH, listOf("app.fluffy")),
        play("xplore", "X-plore", "Quản lý file hai khung: USB, ổ mạng, Google Drive.", "A dual-pane file manager: USB, network drives, Google Drive.", Nhom.TIEN_ICH, "com.lonelycatgames.Xplore"),
        App("tvbro", "TV Bro", "Trình duyệt web cho tivi, điều khiển bằng remote.", "A web browser for TVs, driven by the remote.",
            Nguon.GitHub("truefedex/tv-bro", Regex("""geckoExcluded\.apk$""")), Nhom.TIEN_ICH, listOf("com.phlox.tvwebbrowser")),
        App("leankey", "LeanKey Keyboard", "Bàn phím ảo dễ gõ bằng remote.", "An on-screen keyboard that's easy with a remote.",
            Nguon.GitHub("yuliskov/LeanKeyKeyboard", Regex("""\.apk$""")), Nhom.TIEN_ICH,
            listOf("org.liskovsoft.androidtv.rukeyboard", "com.liskovsoft.leankeyboard", "org.liskovsoft.leankeykeyboard.pro")),
        play("tailscale", "Tailscale", "Mạng riêng (VPN) để xem phim từ máy chủ nhà khi ở xa.", "A private network (VPN) to reach your home server from anywhere.", Nhom.TIEN_ICH, "com.tailscale.ipn"),
        play("anydesk", "AnyDesk", "Điều khiển tivi từ xa qua Internet, để hỗ trợ người thân.", "Control the TV remotely over the Internet to help family.", Nhom.TIEN_ICH, "com.anydesk.anydeskandroid"),
    )

    fun theoId(id: String) = DS.firstOrNull { it.id == id }

    private fun chuanAbi(abi: List<String>) = abi.map { it.trim().lowercase().replace('_', '-') }.filter { it.isNotEmpty() }

    /**
     * Tên trong [ds] ứng với ABI tốt nhất của tivi ([tenAbi]: tên ABI kiểu riêng của dự án); không tên nào ghi ABI thì
     * lấy tên đầu (bản dùng chung).
     */
    fun chonTheoAbi(ds: List<String>, abiTv: List<String>, tenAbi: Map<String, String> = emptyMap()): String? {
        val chuan = ds.associateWith { it.lowercase().replace('_', '-') }
        for (a in chuanAbi(abiTv)) {
            val ten = listOfNotNull(a, tenAbi[a])
            ds.firstOrNull { d -> ten.any { it in chuan.getValue(d) } }?.let { return it }
        }
        val coAbi = Regex("arm64|armeabi|arm-v7|armv7|arm32|x86|x64")
        return ds.firstOrNull { d -> !coAbi.containsMatchIn(chuan.getValue(d)) && tenAbi.values.none { it in chuan.getValue(d) } }
            ?: if (abiTv.isEmpty()) ds.firstOrNull() else null
    }

    /** File nên tải trong một bản phát hành GitHub có các file [tenTep], cho tivi có [abiTv]. */
    fun chonTep(n: Nguon.GitHub, tenTep: List<String>, abiTv: List<String>): String? = chonTheoAbi(tenTep.filter { n.loc.containsMatchIn(it) }, abiTv, n.tenAbi)

    /**
     * File mới nhất khớp [loc] trong trang liệt kê thư mục, đúng [abi] — so với nhóm cuối của [loc] (tên ABI trong tên
     * file), không so "chứa": "x86" nằm trong "x86_64".
     */
    fun chonTrongThuMuc(html: String, loc: Regex, abi: String): String? {
        val chuan = abi.replace('_', '-')
        return loc.findAll(html).filter { it.groupValues.last().replace('_', '-') == chuan }.map { it.value }.distinct()
            .maxWithOrNull(compareBy<String, List<Int>>(Comparator { a, b ->
                (0 until maxOf(a.size, b.size)).map { (a.getOrElse(it) { 0 }).compareTo(b.getOrElse(it) { 0 }) }.firstOrNull { it != 0 } ?: 0
            }) { ten -> Regex("""\d+(\.\d+)+""").find(ten)?.value?.split('.')?.map { it.toInt() } ?: emptyList() })
    }

    private fun lay(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "MTVkit")
        c.setRequestProperty("Accept", "application/vnd.github+json, text/html, */*")
        try {
            if (c.responseCode != 200) error(t("Máy chủ trả lỗi ${c.responseCode} ($url)", "Server error ${c.responseCode} ($url)"))
            return c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally { c.disconnect() }
    }

    /** Link tải bản mới nhất của [a] cho tivi có [abiTv] (gọi trên luồng IO, cần mạng). */
    fun timLink(a: App, abiTv: List<String>): String = timBanMoi(a, abiTv).url

    /** Bản mới nhất: link tải, tên file, phiên bản đọc từ tên file hay tag phát hành (null = không đọc được). */
    data class BanMoi(val url: String, val tep: String, val phienBan: String?)

    fun timBanMoi(a: App, abiTv: List<String>): BanMoi = when (val n = a.nguon) {
        is Nguon.GitHub -> {
            val rel = JSONObject(lay("https://api.github.com/repos/${n.repo}/releases/latest"))
            val tep = rel.getJSONArray("assets")
            val ds = (0 until tep.length()).map { tep.getJSONObject(it) }
            val ten = chonTep(n, ds.map { it.getString("name") }, abiTv) ?: error(t("${a.ten} không có bản cho chip của tivi này.", "${a.ten} has no build for this TV's chip."))
            val link = ds.first { it.getString("name") == ten }.getString("browser_download_url")
            require(TaiLink.urlHopLe(link, batBuocHttps = true, chanMangNoiBo = true)) { "Link tải ${a.ten} không an toàn." }
            BanMoi(link, ten, phienBanTu(ten, rel.optString("tag_name")))
        }
        Nguon.CHPlay -> error(t("${a.ten} cài từ CH Play của tivi.", "${a.ten} installs from the TV's Play Store."))
        is Nguon.ThuMuc -> {
            val abi = chuanAbi(abiTv).firstOrNull { it in n.theoAbi || it.replace('-', '_') in n.theoAbi }
                ?: error(t("${a.ten} không có bản cho chip của tivi này.", "${a.ten} has no build for this TV's chip."))
            val thuMuc = n.theoAbi[abi] ?: n.theoAbi.getValue(abi.replace('-', '_'))
            val ten = chonTrongThuMuc(lay(thuMuc), n.loc, abi) ?: error(t("Không thấy file ${a.ten} trên máy chủ.", "Couldn't find ${a.ten} on the server."))
            val link = thuMuc + ten
            require(TaiLink.urlHopLe(link, batBuocHttps = true, chanMangNoiBo = true)) { "Link tải ${a.ten} không an toàn." }
            BanMoi(link, ten, phienBanTu(ten, null))
        }
    }

    // ---------- So phiên bản (cập nhật app đã cài, 25/10/2026) ----------

    /** Tên ABI trong tên file (bỏ đi trước khi tìm số phiên bản: "arm64-v8a" có số). */
    private val ABI_TRONG_TEN = Regex("arm64-v8a|armeabi-v7a|arm64v8|armv7|x86_64|x86|arm64|arm|universal", RegexOption.IGNORE_CASE)

    /**
     * Phiên bản của bản mới: số trong tên file nếu có (SmartTube_stable_29.15_…, VLC-Android-3.6.5-…, kodi-21.2-Omega-…),
     * không thì tag phát hành bỏ chữ "v" (v1.4.0 → 1.4.0).
     */
    fun phienBanTu(tep: String, tag: String?): String? {
        val tuTep = Regex("""\d+(?:[._]\d+)+""").find(tep.replace(ABI_TRONG_TEN, ""))?.value?.replace('_', '.')
        return tuTep ?: tag?.trim()?.removePrefix("v")?.removePrefix("V")?.takeIf { it.isNotEmpty() && it.any(Char::isDigit) }
    }

    /** Các số của phiên bản: "29.15" → [29, 15], "2024-11-21-release" → [2024, 11, 21]; không có số → null. */
    fun soPhienBan(s: String?): List<Int>? {
        if (s == null) return null
        val ds = Regex("""\d+(?:[._-]\d+)*""").find(s)?.value?.split('.', '_', '-')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() } ?: return null
        // Bỏ hậu tố ngày build kiểu 6.2.85-20241121.1805 nếu phía trước đã có ít nhất 2 cụm số phiên bản.
        val cat = ds.indexOfFirst { it >= 19000101 }
        return if (cat >= 2) ds.subList(0, cat) else ds
    }

    /** [moi] mới hơn [cu]? null = không so được (thiếu số). Số so lần lượt, thiếu coi như 0 (1.2 = 1.2.0). */
    fun moiHon(moi: String?, cu: String?): Boolean? {
        val a = soPhienBan(moi) ?: return null
        val b = soPhienBan(cu) ?: return null
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/** Tải APK từ một đường link (25/10/2026): theo chuyển hướng an toàn, đặt đúng đuôi để GoiApk.tuFile nhận. */
object TaiLink {
    /** Dung lượng tải tối đa cho một gói APK / bundle (1,5 GB) để tránh tràn bộ nhớ điện thoại. */
    const val TOI_DA_TAI = 1536L * 1024 * 1024
    private val RE_IP_THO = Regex("""^(?:0x[0-9a-fA-F]+|\d+)(?:\.(?:0x[0-9a-fA-F]+|\d+)){0,3}$""")

    /** Tên file từ Content-Disposition (filename* / filename) hoặc đoạn cuối của đường dẫn. */
    fun tenFile(url: String, cd: String?): String {
        cd?.let { h ->
            Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)""").find(h)?.let { return URLDecoder.decode(it.groupValues[1].trim(), "UTF-8") }
            Regex("""filename\s*=\s*"?([^";]+)"?""").find(h)?.let { return it.groupValues[1].trim() }
        }
        return URLDecoder.decode(url.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/'), "UTF-8").ifBlank { "app.apk" }
    }

    /** Kiểm tra địa chỉ IP không phải loopback (127.0.0.1, ::1), link-local (169.254.x.x), 0.0.0.0, multicast (và LAN nếu [chanMangNoiBo]). */
    fun diaChiHopLe(dc: java.net.InetAddress, chanMangNoiBo: Boolean = false): Boolean {
        if (dc.isLoopbackAddress || dc.isAnyLocalAddress || dc.isLinkLocalAddress || dc.isMulticastAddress) return false
        val b = dc.address
        // IPv4-mapped IPv6 (::ffff:127.0.0.1) hoặc dải 127.0.0.0/8, 0.0.0.0/8, 169.254.0.0/16.
        if (b.size == 4) {
            val d0 = b[0].toInt() and 0xFF; val d1 = b[1].toInt() and 0xFF
            if (d0 == 0 || d0 == 127 || (d0 == 169 && d1 == 254)) return false
        }
        if (chanMangNoiBo && dc.isSiteLocalAddress) return false
        return true
    }

    /** Kiểm tra cú pháp URL và địa chỉ IP tĩnh (không phân giải DNS). */
    fun urlHopLe(url: String, batBuocHttps: Boolean = false, chanMangNoiBo: Boolean = false): Boolean {
        val s = url.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() || it.code < 0x20 }) return false
        val u = runCatching { URL(s) }.getOrNull() ?: return false
        val giaoThuc = u.protocol.lowercase()
        if (batBuocHttps && giaoThuc != "https") return false
        if (giaoThuc != "http" && giaoThuc != "https") return false
        val host = u.host?.trim()?.trim('[', ']')?.lowercase().orEmpty()
        if (host.isEmpty() || host == "localhost" || host.endsWith(".localhost")) return false
        if (':' in host || RE_IP_THO.matches(host)) {
            val dc = runCatching { java.net.InetAddress.getByName(host) }.getOrNull() ?: return false
            if (!diaChiHopLe(dc, chanMangNoiBo)) return false
        }
        return true
    }

    /** Giải quyết URL chuyển hướng và chặn hạ cấp HTTPS → HTTP hoặc nhảy vào địa chỉ nội bộ. */
    fun buocChuyenHuong(tuUrl: String, location: String?, batBuocHttps: Boolean = false, chanMangNoiBo: Boolean = false): String {
        val loc = location?.trim()?.takeIf { it.isNotEmpty() } ?: error(t("Chuyển hướng không có địa chỉ.", "Redirect missing Location."))
        val moi = runCatching { URL(URL(tuUrl), loc).toString() }.getOrElse { error(t("Địa chỉ chuyển hướng không hợp lệ.", "Invalid redirect URL.")) }
        val canHttps = batBuocHttps || tuUrl.startsWith("https://", ignoreCase = true)
        if (canHttps && !moi.startsWith("https://", ignoreCase = true)) {
            error(t("Chặn chuyển hướng hạ cấp không an toàn (HTTPS → HTTP).", "Blocked unsafe protocol downgrade redirect (HTTPS → HTTP)."))
        }
        if (!urlHopLe(moi, batBuocHttps = canHttps, chanMangNoiBo = chanMangNoiBo)) {
            error(t("Địa chỉ tải không an toàn hoặc không hợp lệ.", "Unsafe or invalid download URL."))
        }
        return moi
    }

    private fun kiemTraKetNoi(url: String, batBuocHttps: Boolean, chanMangNoiBo: Boolean) {
        if (!urlHopLe(url, batBuocHttps = batBuocHttps, chanMangNoiBo = chanMangNoiBo)) {
            error(t("Đường link tải không hợp lệ hoặc không an toàn.", "Invalid or unsafe download link."))
        }
        val host = URL(url).host.trim('[', ']')
        val ds = runCatching { java.net.InetAddress.getAllByName(host) }.getOrNull().orEmpty()
        if (ds.isEmpty() || ds.any { !diaChiHopLe(it, chanMangNoiBo) }) {
            error(t("Máy chủ tải trỏ về địa chỉ nội bộ không an toàn.", "Download host resolves to an unsafe local address."))
        }
    }

    /**
     * Tải [url] vào bộ nhớ đệm, trả về file đã có đuôi đúng (.apk / .apks / .xapk / .apkm). Link không ghi đuôi thì nhìn
     * bên trong: có AndroidManifest.xml là .apk, chứa nhiều .apk là .apks; không phải thì báo lỗi.
     */
    fun tai(ctx: Context, url: String, batBuocHttps: Boolean = false, tienDo: (Long, Long) -> Unit = { _, _ -> }): File {
        var u = url.trim()
        val chanLan = batBuocHttps
        kiemTraKetNoi(u, batBuocHttps = batBuocHttps, chanMangNoiBo = chanLan)
        repeat(8) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false // tự theo để kiểm tra an toàn từng bước chuyển hướng
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "MTVkit")
            try {
                val ma = c.responseCode
                if (ma in 300..399) {
                    u = buocChuyenHuong(u, c.getHeaderField("Location"), batBuocHttps = batBuocHttps, chanMangNoiBo = chanLan)
                    kiemTraKetNoi(u, batBuocHttps = batBuocHttps || u.startsWith("https://", ignoreCase = true), chanMangNoiBo = chanLan)
                    return@repeat
                }
                if (ma != 200) error(t("Máy chủ trả lỗi $ma.", "Server error $ma."))
                val ten = tenFile(u, c.getHeaderField("Content-Disposition"))
                val tong = c.contentLengthLong
                if (tong > TOI_DA_TAI) error(t("File tải quá lớn (vượt 1,5 GB).", "Download file too large (over 1.5 GB)."))
                val tam = File(GoiApk.thuMuc(ctx), "${System.nanoTime()}.tai")
                try {
                    c.inputStream.use { vao ->
                        tam.outputStream().use { ra ->
                            val buf = ByteArray(64 * 1024); var da = 0L
                            while (true) {
                                val n = vao.read(buf); if (n < 0) break
                                da += n
                                if (da > TOI_DA_TAI) error(t("File tải quá lớn (vượt 1,5 GB).", "Download file too large (over 1.5 GB)."))
                                ra.write(buf, 0, n); tienDo(da, tong)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    tam.delete(); throw e
                }
                return datDuoi(tam, ten)
            } finally { c.disconnect() }
        }
        error(t("Link chuyển hướng quá nhiều lần.", "Too many redirects."))
    }

    private fun datDuoi(tam: File, ten: String): File {
        val duoi = ten.substringAfterLast('.', "").lowercase()
        val dung = if (duoi == "apk" || duoi in GoiApk.DUOI_GOI) duoi else runCatching {
            ZipFile(tam).use { z ->
                val ds = z.entries().toList().map { it.name }
                when { "AndroidManifest.xml" in ds -> "apk"; ds.any { it.endsWith(".apk") } -> "apks"; else -> null }
            }
        }.getOrNull()
        if (dung == null) { tam.delete(); error(t("Link này không phải file APK.", "That link is not an APK file.")) }
        val goc = ten.substringBeforeLast('.').ifBlank { "app" }.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)
        val f = File(tam.parentFile, "${System.nanoTime()}-$goc.$dung")
        if (!tam.renameTo(f)) { tam.copyTo(f, overwrite = true); tam.delete() }
        return f
    }
}
