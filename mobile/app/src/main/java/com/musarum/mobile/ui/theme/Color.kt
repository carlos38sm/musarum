package com.musarum.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// ──────────────────────────────────────────────────────────────────────────
// Musarum palette v2 — ébano profundo + oro ámbar quemado.
// Inspirado en reproductores oscuros premium (foto 1, 3 de referencias):
// negro casi puro para el fondo, dorado cálido como único acento vivo,
// grises neutros para jerarquía de texto. Un solo acento = elegancia.
// ──────────────────────────────────────────────────────────────────────────

// ─── Acento oro ámbar quemado (único color vivo) ───
val GoldAmber        = Color(0xFFD4A66B)   // oro vivo, acento principal
val GoldAmberDim     = Color(0xFF8C6A3A)   // oro apagado para estados inactivos
val GoldAmberOn      = Color(0xFF2A1C06)   // texto sobre oro
val GoldAmberBg      = Color(0xFF3A2A14)   // fondo pastilla/chip oro
val GoldAmberOnBg    = Color(0xFFF4D8A8)   // texto sobre fondo oro

// ─── Oscuros (modo principal) ───
val Ebony            = Color(0xFF0A0908)   // fondo base, casi negro puro
val EbonyRaised      = Color(0xFF141210)   // surfaces
val EbonyContainer   = Color(0xFF1D1A17)   // containers (cards, chips)
val EbonyHigh        = Color(0xFF2A2622)   // elevado (menús, dialogs)
val EbonyOutline     = Color(0xFF3A332C)   // bordes sutiles
val EbonyGlass       = Color(0xCC141210)   // superficie glass (80% opaco)

val WarmWhite        = Color(0xFFF0EBE4)   // texto principal, blanco cálido
val WarmGrey         = Color(0xFF9A938A)   // texto secundario
val WarmGreyDim      = Color(0xFF605B55)   // texto deshabilitado

// ─── Rosa como terciario (para destructive/favorito) ───
val RoseAccent       = Color(0xFFEC6A86)
val RoseOn           = Color(0xFF450014)

// ─── Light (modo claro, soporte secundario) ───
val LightBg          = Color(0xFFFAF7F2)
val LightSurface     = Color(0xFFFFFFFF)
val LightContainer   = Color(0xFFF2EDE4)
val LightOnSurface   = Color(0xFF1A1510)
val LightOnVariant   = Color(0xFF5A5248)
val LightOutline     = Color(0xFFB8AF9F)

val ErrorRed         = Color(0xFFBA1A1A)
val DarkErrorRed     = Color(0xFFFFB4AB)
