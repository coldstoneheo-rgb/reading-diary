package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.photo.CameraTempFiles
import com.example.data.photo.InternalDiaryPhotoStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 명세 PHRASE-RECALL-SPEC-2026-11 §3.3 P2 — 사진 저장소 경계. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class DiaryPhotoStoreTest {

  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private lateinit var store: InternalDiaryPhotoStore

  @Before
  fun setUp() {
    store = InternalDiaryPhotoStore(context)
    store.root.deleteRecursively()
  }

  @After
  fun tearDown() {
    store.root.deleteRecursively()
  }

  /** 왼쪽 절반 빨강, 오른쪽 절반 파랑 — 뒤집기 방향 확인용. */
  private fun writePng(width: Int, height: Int, name: String): Uri {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) for (y in 0 until height) {
      bitmap.setPixel(x, y, if (x < width / 2) Color.RED else Color.BLUE)
    }
    val file = File(context.cacheDir, name)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return Uri.fromFile(file)
  }

  // decodeFile 대신 스트림을 직접 닫는다(Windows는 열린 핸들이 있으면 삭제가 실패한다).
  private fun decode(file: File): Bitmap = file.inputStream().use { BitmapFactory.decodeStream(it) }!!

  @Test
  fun save_thenExists_thenDelete_thenAbsent() = runBlocking {
    val rel = store.save(writePng(40, 20, "p.png"), 0f, false)

    assertNotNull("save 결과", rel)
    assertTrue("이름은 UUID.jpg: $rel", rel!!.matches(Regex("[0-9a-f\\-]{36}\\.jpg")))
    val file = store.fileFor(rel)
    assertNotNull("저장 후 fileFor", file)
    assertTrue("저장 후 파일 존재", file!!.isFile)
    assertEquals(40, decode(file).width)

    store.delete(rel)

    assertFalse("삭제 후 파일 부재", file.exists())
    assertNull("삭제 후 fileFor", store.fileFor(rel))
  }

  @Test
  fun root_isUnderNoBackupFilesDir() = runBlocking {
    val rel = store.save(writePng(10, 10, "r.png"), 0f, false)!!

    val file = store.fileFor(rel)!!
    val noBackup = context.noBackupFilesDir.canonicalFile
    assertEquals(File(noBackup, InternalDiaryPhotoStore.DIR_NAME), file.canonicalFile.parentFile)
    assertFalse("filesDir(자동 백업 대상) 바로 아래가 아니다", file.canonicalFile.parentFile == context.filesDir.canonicalFile)
  }

  @Test
  fun save_limitsLongestSideTo2048() = runBlocking {
    // 4100 → inSampleSize 4 → 1025. 긴 변은 MAX_SIDE(2048) 이하.
    val rel = store.save(writePng(4100, 60, "wide.png"), 0f, false)!!

    val saved = decode(store.fileFor(rel)!!)
    assertTrue(maxOf(saved.width, saved.height) <= 2048)
    assertEquals(1025, saved.width)
  }

  @Test
  fun save_appliesRotation() = runBlocking {
    val rel = store.save(writePng(60, 20, "rot.png"), 90f, false)!!

    val saved = decode(store.fileFor(rel)!!)
    assertEquals(20, saved.width)
    assertEquals(60, saved.height)
  }

  @Test
  fun save_appliesFlip() = runBlocking {
    val rel = store.save(writePng(40, 20, "flip.png"), 0f, true)!!

    val saved = decode(store.fileFor(rel)!!)
    // 원본은 왼쪽 빨강 → 뒤집으면 왼쪽 파랑(JPEG 손실 허용: 우세 채널로 판정)
    val left = saved.getPixel(2, 10)
    val right = saved.getPixel(37, 10)
    assertTrue(Color.blue(left) > Color.red(left))
    assertTrue(Color.red(right) > Color.blue(right))
  }

  @Test
  fun save_returnsNullWhenDecodeFails() = runBlocking {
    store.root.mkdirs() // 루트가 이미 있을 때도 실패가 파일을 남기지 않는지 본다
    val broken = File(context.cacheDir, "broken.jpg").apply { writeText("not an image") }

    assertNull(store.save(Uri.fromFile(broken), 0f, false))
    assertNull(store.save(Uri.fromFile(File(context.cacheDir, "missing.jpg")), 0f, false))
    assertEquals("실패 시 빈 파일을 남기지 않는다", 0, store.root.listFiles()?.size ?: 0)
  }

  @Test
  fun fileFor_rejectsPathEscape() = runBlocking {
    val rel = store.save(writePng(10, 10, "e.png"), 0f, false)!!
    val outside = File(context.noBackupFilesDir, "secret.jpg").apply { writeText("x") }
    try {
      assertNull(store.fileFor("../secret.jpg"))
      assertNull(store.fileFor("../${InternalDiaryPhotoStore.DIR_NAME}/../secret.jpg"))
      assertNull(store.fileFor(outside.absolutePath))
      assertNull(store.fileFor(""))
      assertNull(store.fileFor("."))
      File(store.root, "sub").mkdirs()
      File(store.root, "sub/x.jpg").writeText("x")
      assertNull("하위 폴더 경로는 거부", store.fileFor("sub/x.jpg"))
      store.delete("../secret.jpg")
      assertTrue("저장소 밖 파일은 delete로도 지워지지 않는다", outside.exists())
      assertNotNull(store.fileFor(rel))
    } finally {
      outside.delete()
    }
  }

  @Test
  fun fileFor_nullWhenFileMissing() {
    assertNull(store.fileFor("00000000-0000-0000-0000-000000000000.jpg"))
  }

  @Test
  fun sweep_deletesOnlyUnreferenced() = runBlocking {
    val keep = store.save(writePng(10, 10, "k.png"), 0f, false)!!
    val drop1 = store.save(writePng(10, 10, "d1.png"), 0f, false)!!
    val drop2 = store.save(writePng(10, 10, "d2.png"), 0f, false)!!
    val outside = File(context.noBackupFilesDir, "keep-outside.jpg").apply { writeText("x") }
    val nested = File(store.root, "sub/y.jpg").apply { parentFile!!.mkdirs(); writeText("x") }
    try {
      val removed = store.sweep(setOf(keep, "../keep-outside.jpg", "dangling.jpg"))
      assertTrue("sweep은 하위 폴더를 건드리지 않는다", nested.exists())

      assertEquals(2, removed)
      assertNotNull(store.fileFor(keep))
      assertNull(store.fileFor(drop1))
      assertNull(store.fileFor(drop2))
      assertTrue("sweep은 루트 밖을 건드리지 않는다", outside.exists())
      assertEquals(0, store.sweep(setOf(keep)))
    } finally {
      outside.delete()
    }
  }

  @Test
  fun sweep_onMissingRoot_returnsZero() = runBlocking {
    assertEquals(0, store.sweep(emptySet()))
  }

  /**
   * 크기 실측 프로브(명세 §3.3 :167). 환경변수 DIARY_PHOTO_PROBE에 실제 촬영 JPEG 경로가 있을 때만 돈다(평소엔 건너뜀).
   * 결과는 표준 출력 1줄 — 이 PR 본문에 기록한다.
   */
  @Test
  fun probe_realPhotoSize() = runBlocking {
    val path = System.getenv("DIARY_PHOTO_PROBE")
    org.junit.Assume.assumeTrue(path != null && File(path).isFile)
    val src = File(path!!)

    val rel = store.save(Uri.fromFile(src), 0f, false)!!
    val saved = store.fileFor(rel)!!
    val bmp = decode(saved)
    println("DIARY_PHOTO_PROBE src=${src.length()}B -> saved=${saved.length()}B ${bmp.width}x${bmp.height}")
    assertTrue(maxOf(bmp.width, bmp.height) <= 2048)
  }

  @Test
  fun cameraTempFiles_deletesOnlyCameraPhotos() {
    val dir = File(context.cacheDir, "temp-test").apply { deleteRecursively(); mkdirs() }
    val a = File.createTempFile(CameraTempFiles.PREFIX, CameraTempFiles.SUFFIX, dir)
    val b = File.createTempFile(CameraTempFiles.PREFIX, CameraTempFiles.SUFFIX, dir)
    val other = File(dir, "coil_cache.jpg").apply { writeText("x") }
    val png = File(dir, "${CameraTempFiles.PREFIX}x.png").apply { writeText("x") }

    assertEquals(2, CameraTempFiles.deleteAll(dir))
    assertFalse(a.exists())
    assertFalse(b.exists())
    assertTrue(other.exists())
    assertTrue(png.exists())
    dir.deleteRecursively()
  }
}
