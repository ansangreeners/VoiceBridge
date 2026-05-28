package com.voicebridge.app

/**
 * HID 키보드 상수 + 문자→키코드 매핑
 *
 * 한글 처리 방식:
 *  PC의 한글 IME(두벌식)가 켜진 상태에서, 한글 음절을 자모로 분해해
 *  대응하는 QWERTY 키를 순서대로 보내면 IME가 조합해 한글이 완성됨.
 *  (예: '한' → ㅎ(g) ㅏ(k) ㄴ(s) → 한)
 *  영문/숫자는 그대로 키코드 전송.
 */
object HidConst {

    // 표준 키보드 HID 리포트 디스크립터
    val REPORT_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),  // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),  // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),  // Collection (Application)
        0x05.toByte(), 0x07.toByte(),  // Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(),  // Usage Min (224)
        0x29.toByte(), 0xE7.toByte(),  // Usage Max (231)
        0x15.toByte(), 0x00.toByte(),  // Logical Min (0)
        0x25.toByte(), 0x01.toByte(),  // Logical Max (1)
        0x75.toByte(), 0x01.toByte(),  // Report Size (1)
        0x95.toByte(), 0x08.toByte(),  // Report Count (8)
        0x81.toByte(), 0x02.toByte(),  // Input (Data, Variable, Absolute) — modifiers
        0x95.toByte(), 0x01.toByte(),  // Report Count (1)
        0x75.toByte(), 0x08.toByte(),  // Report Size (8)
        0x81.toByte(), 0x01.toByte(),  // Input (Constant) — reserved
        0x95.toByte(), 0x06.toByte(),  // Report Count (6)
        0x75.toByte(), 0x08.toByte(),  // Report Size (8)
        0x15.toByte(), 0x00.toByte(),  // Logical Min (0)
        0x25.toByte(), 0x65.toByte(),  // Logical Max (101)
        0x05.toByte(), 0x07.toByte(),  // Usage Page (Key Codes)
        0x19.toByte(), 0x00.toByte(),  // Usage Min (0)
        0x29.toByte(), 0x65.toByte(),  // Usage Max (101)
        0x81.toByte(), 0x00.toByte(),  // Input (Data, Array) — keys
        0xC0.toByte()                  // End Collection
    )

    private const val SHIFT = 0x02

    // 영문 소문자/숫자 → HID 키코드
    private val base: Map<Char, Int> = buildMap {
        // a-z
        val letters = "abcdefghijklmnopqrstuvwxyz"
        for (i in letters.indices) put(letters[i], 0x04 + i)
        // 1-9,0
        put('1',0x1E);put('2',0x1F);put('3',0x20);put('4',0x21);put('5',0x22)
        put('6',0x23);put('7',0x24);put('8',0x25);put('9',0x26);put('0',0x27)
        put(' ',0x2C);put('\n',0x28);put('\t',0x2B)
        put('-',0x2D);put('=',0x2E);put('[',0x2F);put(']',0x30);put('\\',0x31)
        put(';',0x33);put('\'',0x34);put('`',0x35);put(',',0x36);put('.',0x37);put('/',0x38)
    }

    // shift 필요한 문자
    private val shifted: Map<Char, Int> = buildMap {
        put('!',0x1E);put('@',0x1F);put('#',0x20);put('$',0x21);put('%',0x22)
        put('^',0x23);put('&',0x24);put('*',0x25);put('(',0x26);put(')',0x27)
        put('_',0x2D);put('+',0x2E);put('{',0x2F);put('}',0x30);put('|',0x31)
        put(':',0x33);put('"',0x34);put('~',0x35);put('<',0x36);put('>',0x37);put('?',0x38)
    }

    // 한글 자모 → 두벌식 QWERTY 키 문자
    private val jamoToKey: Map<Char, String> = buildMap {
        // 초성/종성 자음
        put('ㄱ',"r");put('ㄲ',"R");put('ㄴ',"s");put('ㄷ',"e");put('ㄸ',"E")
        put('ㄹ',"f");put('ㅁ',"a");put('ㅂ',"q");put('ㅃ',"Q");put('ㅅ',"t")
        put('ㅆ',"T");put('ㅇ',"d");put('ㅈ',"w");put('ㅉ',"W");put('ㅊ',"c")
        put('ㅋ',"z");put('ㅌ',"x");put('ㅍ',"v");put('ㅎ',"g")
        // 중성 모음
        put('ㅏ',"k");put('ㅐ',"o");put('ㅑ',"i");put('ㅒ',"O");put('ㅓ',"j")
        put('ㅔ',"p");put('ㅕ',"u");put('ㅖ',"P");put('ㅗ',"h");put('ㅛ',"y")
        put('ㅜ',"n");put('ㅠ',"b");put('ㅡ',"m");put('ㅣ',"l")
        // 복합 모음 (조합)
        put('ㅘ',"hk");put('ㅙ',"ho");put('ㅚ',"hl");put('ㅝ',"nj")
        put('ㅞ',"np");put('ㅟ',"nl");put('ㅢ',"ml")
        // 복합 받침 (조합)
        put('ㄳ',"rt");put('ㄵ',"sw");put('ㄶ',"sg");put('ㄺ',"fr");put('ㄻ',"fa")
        put('ㄼ',"fq");put('ㄽ',"ft");put('ㄾ',"fx");put('ㄿ',"fv");put('ㅀ',"fg")
        put('ㅄ',"qt")
    }

    private val CHO = arrayOf('ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ','ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ')
    private val JUNG = arrayOf('ㅏ','ㅐ','ㅑ','ㅒ','ㅓ','ㅔ','ㅕ','ㅖ','ㅗ','ㅘ','ㅙ','ㅚ','ㅛ','ㅜ','ㅝ','ㅞ','ㅟ','ㅠ','ㅡ','ㅢ','ㅣ')
    private val JONG = arrayOf(' ','ㄱ','ㄲ','ㄳ','ㄴ','ㄵ','ㄶ','ㄷ','ㄹ','ㄺ','ㄻ','ㄼ','ㄽ','ㄾ','ㄿ','ㅀ','ㅁ','ㅂ','ㅄ','ㅅ','ㅆ','ㅇ','ㅈ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ')

    /**
     * 한글 음절을 두벌식 키 시퀀스(영문)로 분해
     */
    fun decomposeHangul(ch: Char): String? {
        val code = ch.code
        if (code !in 0xAC00..0xD7A3) return null
        val s = code - 0xAC00
        val cho = s / (21 * 28)
        val jung = (s % (21 * 28)) / 28
        val jong = s % 28
        val sb = StringBuilder()
        sb.append(jamoToKey[CHO[cho]] ?: "")
        sb.append(jamoToKey[JUNG[jung]] ?: "")
        if (jong > 0) sb.append(jamoToKey[JONG[jong]] ?: "")
        return sb.toString()
    }

    /**
     * 문자 하나 → (modifier, keyCode).
     * 한글이면 호출부에서 decomposeHangul로 먼저 분해해야 함.
     * 여기서는 단일 영문/숫자/기호 1글자만 처리.
     */
    fun charToKey(ch: Char): Pair<Int, Int> {
        base[ch]?.let { return 0 to it }
        base[ch.lowercaseChar()]?.let {
            if (ch.isUpperCase()) return SHIFT to it
        }
        shifted[ch]?.let { return SHIFT to it }
        if (ch in 'A'..'Z') return SHIFT to (0x04 + (ch - 'A'))
        return 0 to 0
    }
}
