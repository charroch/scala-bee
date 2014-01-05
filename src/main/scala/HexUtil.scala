package hubstep.utils

object HexUtil {

  implicit class HexRichString(s: String) {
    def hex: Array[Byte] = s.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
  }

}