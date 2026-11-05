package com.example.data.photo

import java.io.File

/** 화면에 그릴 보관 사진 1장. [file]이 null이면 행은 있고 파일이 없는 dangling이다(«사진 없음(이 기기에 없음)», 명세 §2.4). */
data class RecallPhoto(val photoId: Int, val file: File?)
