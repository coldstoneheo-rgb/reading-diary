package com.example

import android.net.Uri
import com.example.ui.viewmodel.PhotoDrafts
import com.example.ui.viewmodel.PhotoInput
import com.example.ui.viewmodel.PhotoInputSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoDraftsTest {

  private val uri = Uri.parse("content://photos/1")
  private val other = Uri.parse("file:///cache/a%7Cb.jpg")

  @Test
  fun untouchedList_isUnchanged_notAnEmptySet() {
    val drafts = listOf(PhotoInputSlot.Existing(1), PhotoInputSlot.Existing(2))
    assertEquals(PhotoInput.Unchanged, PhotoDrafts.toInput(drafts, listOf(1, 2)))
    assertEquals(PhotoInput.Unchanged, PhotoDrafts.toInput(emptyList(), emptyList()))
  }

  @Test
  fun removingAll_isAnEmptySet_notUnchanged() {
    assertEquals(PhotoInput.Slots(emptyList()), PhotoDrafts.toInput(emptyList(), listOf(1)))
  }

  @Test
  fun reordering_isNotUnchanged() {
    val drafts = listOf(PhotoInputSlot.Existing(2), PhotoInputSlot.Existing(1))
    assertTrue(PhotoDrafts.toInput(drafts, listOf(1, 2)) is PhotoInput.Slots)
  }

  @Test
  fun replace_keepsExistingIdForFallback_andNewStaysNew() {
    val drafts = listOf(PhotoInputSlot.Existing(1), PhotoInputSlot.New(uri))
    val first = PhotoDrafts.replaceAt(drafts, 0, other)
    assertEquals(PhotoInputSlot.Replace(1, other), first[0])
    assertEquals(PhotoInputSlot.Replace(1, uri), PhotoDrafts.replaceAt(first, 0, uri)[0])
    assertEquals(PhotoInputSlot.New(other), PhotoDrafts.replaceAt(drafts, 1, other)[1])
  }

  @Test
  fun outOfRangeIndex_changesNothing() {
    val drafts = listOf(PhotoInputSlot.Existing(1))
    assertEquals(drafts, PhotoDrafts.removeAt(drafts, 5))
    assertEquals(drafts, PhotoDrafts.replaceAt(drafts, -1, uri))
  }

  @Test
  fun add_stopsAtTheLimit() {
    val full = List(PhotoDrafts.MAX_PER_DIARY) { PhotoInputSlot.Existing(it) }
    assertFalse(PhotoDrafts.canAdd(full))
    assertEquals(full, PhotoDrafts.add(full, uri))
    assertEquals(PhotoDrafts.MAX_PER_DIARY, PhotoDrafts.add(full.drop(1), uri).size)
  }

  @Test
  fun encode_roundTrips_includingUriWithSeparator() {
    val drafts = listOf(
      PhotoInputSlot.Existing(7),
      PhotoInputSlot.New(other, 90f, true),
      PhotoInputSlot.Replace(3, Uri.parse("content://x/a|b"), 180f, false)
    )
    assertEquals(drafts, PhotoDrafts.decode(PhotoDrafts.encode(drafts)))
  }
}
