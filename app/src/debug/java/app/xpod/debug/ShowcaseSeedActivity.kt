package app.xpod.debug

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import app.xpod.MainActivity
import app.xpod.R
import app.xpod.data.AppTab
import app.xpod.data.ArticleEntity
import app.xpod.data.ArticleFeedEntity
import app.xpod.data.EpisodeEntity
import app.xpod.data.LocalTrackEntity
import app.xpod.data.PlaybackMediaType
import app.xpod.data.PlaybackStateEntity
import app.xpod.data.PodcastEntity
import app.xpod.data.QueueItemEntity
import app.xpod.data.SettingsRepository
import app.xpod.data.ThemeMode
import app.xpod.data.XpodDatabase
import app.xpod.playback.PlaybackController
import app.xpod.playback.PlaybackStatus
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class ShowcaseSeedActivity : ComponentActivity() {
  @Inject lateinit var seeder: ShowcaseDataSeeder

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleScope.launch {
      runCatching { seeder.replaceDebugData() }
          .onSuccess {
            startActivity(
                Intent(this@ShowcaseSeedActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
          }
          .onFailure { error ->
            Log.e(TAG, "Unable to prepare showcase data", error)
            Toast.makeText(
                    this@ShowcaseSeedActivity,
                    "Unable to prepare showcase data",
                    Toast.LENGTH_LONG,
                )
                .show()
            finish()
          }
    }
  }

  private companion object {
    const val TAG = "XPOD-Showcase"
  }
}

class ShowcaseDataSeeder
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val database: XpodDatabase,
    private val settings: SettingsRepository,
    private val player: PlaybackController,
) {
  suspend fun replaceDebugData() {
    withTimeoutOrNull(5_000) { runCatching { player.clearQueue() } }

    val media =
        ShowcaseMedia(
            spaceArtwork = resourceUri(R.drawable.showcase_cover_space),
            cityArtwork = resourceUri(R.drawable.showcase_cover_city),
            technologyArtwork = resourceUri(R.drawable.showcase_cover_technology),
            natureArtwork = resourceUri(R.drawable.showcase_cover_nature),
            audio = resourceUri(R.raw.showcase_silence),
        )
    val data = showcaseData(media)

    withContext(Dispatchers.IO) {
      database.clearAllTables()
      database.withTransaction {
        data.podcasts.forEach { database.podcasts().upsert(it) }
        database.episodes().upsertAll(data.episodes)
        data.articleFeeds.forEach { database.articleFeeds().upsert(it) }
        database.articles().upsertAll(data.articles)
        database.localTracks().upsertAll(data.tracks)
        database
            .playback()
            .save(
                PlaybackStateEntity(
                    key = PlaybackMediaType.Podcast.name,
                    mediaId = data.nowPlayingEpisodeId,
                    mediaType = PlaybackMediaType.Podcast.name,
                    positionMs = SHOWCASE_POSITION_MS,
                    speed = SHOWCASE_SPEED,
                    updatedAtEpochMs = SHOWCASE_NOW_MS,
                )
            )
        database
            .playback()
            .insertQueue(
                data.queueEpisodeIds.mapIndexed { index, id ->
                  QueueItemEntity(id, PlaybackMediaType.Podcast.name, index)
                }
            )
      }
    }

    settings.setDynamicColor(false)
    settings.setAppTheme(ThemeMode.Light)
    settings.setDefaultSpeed(SHOWCASE_SPEED)
    settings.setLocalMusicTreeUri(SHOWCASE_MUSIC_TREE_URI)
    settings.setTabEnabled(AppTab.Memos, false)

    val nowPlaying = data.episodes.first { it.id == data.nowPlayingEpisodeId }
    player.play(nowPlaying)
    data.queueEpisodeIds
        .drop(1)
        .mapNotNull { id -> data.episodes.firstOrNull { it.id == id } }
        .forEach { player.addToQueue(it) }
    withTimeoutOrNull(5_000) {
      player.nowPlaying.filterNotNull().first { it.status == PlaybackStatus.Playing }
    }
    player.seekTo(SHOWCASE_POSITION_MS)
    if (player.nowPlaying.value?.isPlaying == true) player.toggle()
    delay(2_200)
  }

  private fun resourceUri(id: Int): String =
      Uri.Builder()
          .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
          .authority(context.packageName)
          .appendPath(id.toString())
          .build()
          .toString()
}

internal data class ShowcaseMedia(
    val spaceArtwork: String,
    val cityArtwork: String,
    val technologyArtwork: String,
    val natureArtwork: String,
    val audio: String,
)

internal data class ShowcaseData(
    val podcasts: List<PodcastEntity>,
    val episodes: List<EpisodeEntity>,
    val articleFeeds: List<ArticleFeedEntity>,
    val articles: List<ArticleEntity>,
    val tracks: List<LocalTrackEntity>,
    val nowPlayingEpisodeId: String,
    val queueEpisodeIds: List<String>,
)

internal fun showcaseData(media: ShowcaseMedia): ShowcaseData {
  val podcasts =
      listOf(
          PodcastEntity(
              id = "showcase-podcast-city",
              feedUrl = "https://showcase.invalid/podcasts/city.xml",
              title = "城市切片",
              author = "街角录音室",
              description = "从声音出发，观察一座城市每天发生的细小变化。",
              artworkUrl = media.cityArtwork,
              lastRefreshEpochMs = SHOWCASE_NOW_MS,
          ),
          PodcastEntity(
              id = "showcase-podcast-nature",
              feedUrl = "https://showcase.invalid/podcasts/nature.xml",
              title = "微小宇宙",
              author = "观察者笔记",
              description = "把镜头靠近一些，重新认识身边的自然。",
              artworkUrl = media.natureArtwork,
              lastRefreshEpochMs = SHOWCASE_NOW_MS,
          ),
          PodcastEntity(
              id = "showcase-podcast-technology",
              feedUrl = "https://showcase.invalid/podcasts/technology.xml",
              title = "技术余温",
              author = "慢热科技",
              description = "聊工具，也聊工具背后的人与选择。",
              artworkUrl = media.technologyArtwork,
              lastRefreshEpochMs = SHOWCASE_NOW_MS,
          ),
          PodcastEntity(
              id = "showcase-podcast-space",
              feedUrl = "https://showcase.invalid/podcasts/space.xml",
              title = "深空漫游",
              author = "地平线电台",
              description = "从一束微光出发，听见宇宙的尺度。",
              artworkUrl = media.spaceArtwork,
              lastRefreshEpochMs = SHOWCASE_NOW_MS,
          ),
      )

  val episodes =
      listOf(
          episode(
              id = "showcase-episode-city-1",
              podcast = podcasts[0],
              title = "在清晨六点，听见一座城",
              description = "跟随第一班公交、早餐铺和清洁车，记录城市醒来的声音。",
              published = "2026-08-06T22:00:00Z",
              durationMinutes = 45,
              isFavorite = true,
              isNew = true,
              lastPlayedEpochMs = SHOWCASE_NOW_MS,
              audio = media.audio,
          ),
          episode(
              id = "showcase-episode-city-2",
              podcast = podcasts[0],
              title = "街角的一百种停留方式",
              description = "长椅、树荫和临街小店，怎样让陌生人共享同一段时间。",
              published = "2026-08-02T10:00:00Z",
              durationMinutes = 38,
              audio = media.audio,
          ),
          episode(
              id = "showcase-episode-city-3",
              podcast = podcasts[0],
              title = "雨落下来以后",
              description = "从屋檐到下水道，一场夏雨如何改变城市的节奏。",
              published = "2026-07-27T10:00:00Z",
              durationMinutes = 32,
              isPlayed = true,
              lastPlayedEpochMs = epochMs("2026-07-28T01:30:00Z"),
              audio = media.audio,
          ),
          episode(
              id = "showcase-episode-nature-1",
              podcast = podcasts[1],
              title = "一平方米里的盛夏",
              description = "不去远方，只观察窗边一平方米的生命如何度过八月。",
              published = "2026-08-05T09:00:00Z",
              durationMinutes = 41,
              isNew = true,
              audio = media.audio,
          ),
          episode(
              id = "showcase-episode-nature-2",
              podcast = podcasts[1],
              title = "苔藓保存的天气",
              description = "一片苔藓里，藏着湿度、方向与时间留下的线索。",
              published = "2026-07-30T09:00:00Z",
              durationMinutes = 36,
              isFavorite = true,
              lastPlayedEpochMs = epochMs("2026-08-01T02:00:00Z"),
              audio = media.audio,
          ),
          episode(
              id = "showcase-episode-nature-3",
              podcast = podcasts[1],
              title = "夜行动物经过花园",
              description = "当人类睡去，另一套安静的城市交通才刚刚开始。",
              published = "2026-07-22T09:00:00Z",
              durationMinutes = 47,
              isPlayed = true,
              audio = media.audio,
          ),
          episode(
              id = "showcase-episode-technology-1",
              podcast = podcasts[2],
              title = "让工具退后一步",
              description = "好的软件为何不该不断争夺注意力，而应在需要时恰好出现。",
              published = "2026-08-04T12:00:00Z",
              durationMinutes = 52,
              isFavorite = true,
              isNew = true,
              audio = media.audio,
          ),
          episode(
              id = "showcase-episode-technology-2",
              podcast = podcasts[2],
              title = "慢一点写出的代码",
              description = "从命名、删除和留白开始，谈谈工程里的耐心。",
              published = "2026-07-29T12:00:00Z",
              durationMinutes = 43,
              lastPlayedEpochMs = epochMs("2026-08-03T07:40:00Z"),
              audio = media.audio,
          ),
          episode(
              id = "showcase-episode-technology-3",
              podcast = podcasts[2],
              title = "离线仍然可用",
              description = "本地优先不只是技术选择，也是一种对用户边界的尊重。",
              published = "2026-07-20T12:00:00Z",
              durationMinutes = 35,
              isPlayed = true,
              audio = media.audio,
          ),
          episode(
              id = "showcase-episode-space-1",
              podcast = podcasts[3],
              title = "当我们开始聆听宇宙",
              description = "无线电望远镜如何把遥远天体的信号，变成可以理解的故事。",
              published = "2026-08-03T14:00:00Z",
              durationMinutes = 49,
              isNew = true,
              audio = media.audio,
          ),
          episode(
              id = "showcase-episode-space-2",
              podcast = podcasts[3],
              title = "沿着一束光旅行",
              description = "从太阳表面到地球视网膜，一束光经历了怎样的旅程。",
              published = "2026-07-26T14:00:00Z",
              durationMinutes = 44,
              isFavorite = true,
              audio = media.audio,
          ),
          episode(
              id = "showcase-episode-space-3",
              podcast = podcasts[3],
              title = "夜空为什么仍然黑暗",
              description = "一个看似简单的问题，如何指向宇宙的年龄与边界。",
              published = "2026-07-18T14:00:00Z",
              durationMinutes = 39,
              isPlayed = true,
              audio = media.audio,
          ),
      )

  val articleFeeds =
      listOf(
          ArticleFeedEntity(
              id = "showcase-feed-slow-reading",
              feedUrl = "https://showcase.invalid/articles/slow-reading.xml",
              title = "慢读周刊",
              author = "慢读编辑部",
              description = "为值得完整读完的内容留出时间。",
              artworkUrl = media.cityArtwork,
              lastRefreshEpochMs = SHOWCASE_NOW_MS,
          ),
          ArticleFeedEntity(
              id = "showcase-feed-methods",
              feedUrl = "https://showcase.invalid/articles/methods.xml",
              title = "方法之间",
              author = "实践笔记",
              description = "记录工具、方法和创造过程。",
              artworkUrl = media.technologyArtwork,
              lastRefreshEpochMs = SHOWCASE_NOW_MS,
          ),
          ArticleFeedEntity(
              id = "showcase-feed-field-notes",
              feedUrl = "https://showcase.invalid/articles/field-notes.xml",
              title = "野外笔记",
              author = "微观观察室",
              description = "从日常附近开始的自然观察。",
              artworkUrl = media.natureArtwork,
              lastRefreshEpochMs = SHOWCASE_NOW_MS,
          ),
      )

  val articles =
      listOf(
          article(
              id = "showcase-article-attention",
              feed = articleFeeds[0],
              title = "把注意力还给一段完整的声音",
              author = "林岸",
              published = "2026-08-07T00:30:00Z",
              artwork = media.cityArtwork,
              favorite = true,
              content =
                  """
                  <h2>从按下播放开始</h2>
                  <p>我们很少真正缺少内容，缺少的是一段没有被打断的时间。声音的特别之处，在于它不要求眼睛一直停留在屏幕上，却仍然能建立完整而细腻的叙事。</p>
                  <p>一次散步、一段通勤，或者做晚饭的半小时，都可以成为认真聆听的空间。重要的不是把列表尽快清空，而是允许一段声音按照自己的节奏展开。</p>
                  <blockquote>好的播放工具不催促你，它只是替你记住停下来的地方。</blockquote>
                  <h2>让界面安静下来</h2>
                  <p>当播放开始之后，界面可以退到背景。清楚的进度、可靠的队列和随时可用的离线内容，已经足够支持大多数时刻。</p>
                  <p>下一次戴上耳机时，不妨只选择一个主题，给它完整的四十分钟。注意力并不会因此减少，反而会慢慢恢复形状。</p>
                  """
                      .trimIndent(),
          ),
          article(
              id = "showcase-article-city-morning",
              feed = articleFeeds[0],
              title = "一座城市如何在清晨醒来",
              author = "周岚",
              published = "2026-08-05T01:00:00Z",
              artwork = media.cityArtwork,
              content =
                  """
                  <p>天色还没有完全亮，路口已经出现第一批有明确目的地的人。早餐铺升起蒸汽，公交站的电子屏开始刷新，城市从许多微小动作中醒来。</p>
                  <h2>声音比光更早</h2>
                  <p>卷帘门、扫帚、远处的发动机与鸟鸣，共同组成了清晨短暂的声景。再晚一些，它们就会被更大的音量覆盖。</p>
                  <p>观察城市并不总需要登上高处。有时只要在同一个街角，多停留十分钟。</p>
                  """
                      .trimIndent(),
              read = true,
          ),
          article(
              id = "showcase-article-quiet-tools",
              feed = articleFeeds[1],
              title = "当工具变得安静，创造才真正开始",
              author = "顾原",
              published = "2026-08-06T03:00:00Z",
              artwork = media.technologyArtwork,
              favorite = true,
              content =
                  """
                  <p>成熟的工具不需要每分钟证明自己的存在。它保存状态、尊重撤销，也允许用户在没有网络时继续完成手边的工作。</p>
                  <h2>少一些打断</h2>
                  <p>通知、徽标和推荐都可能有用，但它们不应该成为默认的节奏。真正顺手的系统会把决定权留给使用者。</p>
                  <ul><li>状态清晰而稳定</li><li>数据边界容易理解</li><li>重要操作可以恢复</li></ul>
                  <p>当这些基础足够可靠，人们才可以忘记工具本身，把注意力放回正在创造的东西上。</p>
                  """
                      .trimIndent(),
          ),
          article(
              id = "showcase-article-local-first",
              feed = articleFeeds[1],
              title = "本地优先，是一种可以感知的边界",
              author = "顾原",
              published = "2026-08-01T03:00:00Z",
              artwork = media.technologyArtwork,
              content =
                  """
                  <p>当数据首先保存在自己的设备里，网络连接从必需条件变成了可选能力。边界更容易解释，离线也不再是异常状态。</p>
                  <p>这种选择并不拒绝同步，而是让同步建立在明确授权之上。</p>
                  """
                      .trimIndent(),
              read = true,
          ),
          article(
              id = "showcase-article-square-meter",
              feed = articleFeeds[2],
              title = "在一平方米里观察四季",
              author = "许知微",
              published = "2026-08-04T06:00:00Z",
              artwork = media.natureArtwork,
              content =
                  """
                  <p>自然观察不一定要从远行开始。固定选择窗边、楼下或通勤路上的一平方米，重复记录，变化就会逐渐显现。</p>
                  <h2>建立一份简单记录</h2>
                  <p>日期、天气、看见的物种和一小段描述已经足够。准确不是第一目标，持续才是。</p>
                  <blockquote>当观察发生在同一个地方，时间本身就成了最重要的变量。</blockquote>
                  <p>几周之后回看，你会发现那些以为从未改变的角落，其实一直在缓慢移动。</p>
                  """
                      .trimIndent(),
          ),
          article(
              id = "showcase-article-moss-weather",
              feed = articleFeeds[2],
              title = "苔藓保存的天气",
              author = "许知微",
              published = "2026-07-28T06:00:00Z",
              artwork = media.natureArtwork,
              content =
                  """
                  <p>苔藓没有年轮，却会用颜色、含水量和生长方向记录环境。靠近地面观察，一场雨留下的痕迹可以停留很多天。</p>
                  <p>它提醒我们，天气不仅发生在天空，也发生在每一处表面。</p>
                  """
                      .trimIndent(),
              read = true,
              favorite = true,
          ),
      )

  val trackTreeUri = SHOWCASE_MUSIC_TREE_URI
  val tracks =
      listOf(
          localTrack(1, "晨雾缓慢散开", "林间信号", "清晨采样", 4, 18, trackTreeUri, media.audio),
          localTrack(2, "穿过旧街", "折线乐团", "城市散步", 3, 47, trackTreeUri, media.audio),
          localTrack(3, "灯塔以北", "岸边计划", "远方来信", 5, 12, trackTreeUri, media.audio),
          localTrack(4, "微光轨道", "无重力合唱团", "深夜飞行", 4, 36, trackTreeUri, media.audio),
          localTrack(5, "树影之间", "夏末二重奏", "绿色房间", 3, 58, trackTreeUri, media.audio),
          localTrack(6, "雨后站台", "慢速列车", "沿途天气", 4, 9, trackTreeUri, media.audio),
          localTrack(7, "夜航模式", "静默频道", "低空云层", 5, 26, trackTreeUri, media.audio),
          localTrack(8, "最后一班电车", "城市回声", "零点之后", 4, 44, trackTreeUri, media.audio),
      )

  val queueIds =
      listOf(
          "showcase-episode-city-1",
          "showcase-episode-technology-1",
          "showcase-episode-space-1",
          "showcase-episode-nature-1",
      )
  return ShowcaseData(
      podcasts = podcasts,
      episodes = episodes,
      articleFeeds = articleFeeds,
      articles = articles,
      tracks = tracks,
      nowPlayingEpisodeId = queueIds.first(),
      queueEpisodeIds = queueIds,
  )
}

private fun episode(
    id: String,
    podcast: PodcastEntity,
    title: String,
    description: String,
    published: String,
    durationMinutes: Int,
    audio: String,
    isPlayed: Boolean = false,
    isFavorite: Boolean = false,
    isNew: Boolean = false,
    lastPlayedEpochMs: Long = 0L,
) =
    EpisodeEntity(
        id = id,
        podcastId = podcast.id,
        stableKey = id,
        title = title,
        description = description,
        audioUrl = audio,
        publishedEpochMs = epochMs(published),
        durationMs = durationMinutes * 60_000L,
        artworkUrl = podcast.artworkUrl,
        isPlayed = isPlayed,
        isFavorite = isFavorite,
        isNew = isNew,
        lastPlayedEpochMs = lastPlayedEpochMs,
    )

private fun article(
    id: String,
    feed: ArticleFeedEntity,
    title: String,
    author: String,
    published: String,
    artwork: String,
    content: String,
    read: Boolean = false,
    favorite: Boolean = false,
) =
    ArticleEntity(
        id = id,
        feedId = feed.id,
        stableKey = id,
        title = title,
        author = author,
        content = content,
        url = "https://showcase.invalid/articles/$id",
        publishedEpochMs = epochMs(published),
        artworkUrl = artwork,
        isRead = read,
        isFavorite = favorite,
    )

private fun localTrack(
    index: Int,
    title: String,
    artist: String,
    album: String,
    minutes: Int,
    seconds: Int,
    treeUri: String,
    audio: String,
) =
    LocalTrackEntity(
        id = "local:showcase-track-${index.toString().padStart(2, '0')}",
        documentUri = audio,
        treeUri = treeUri,
        title = title,
        artist = artist,
        album = album,
        durationMs = (minutes * 60L + seconds) * 1_000L,
        modifiedEpochMs = SHOWCASE_NOW_MS - index * 86_400_000L,
    )

private fun epochMs(value: String): Long = Instant.parse(value).toEpochMilli()

private const val SHOWCASE_MUSIC_TREE_URI = "content://app.xpod.showcase/music"
private const val SHOWCASE_POSITION_MS = 18 * 60_000L + 24_000L
private const val SHOWCASE_SPEED = 1.25f
private val SHOWCASE_NOW_MS = epochMs("2026-08-07T01:00:00Z")
