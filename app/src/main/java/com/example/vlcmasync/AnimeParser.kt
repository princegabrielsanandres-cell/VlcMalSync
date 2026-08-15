package com.example.vlcmasync
import java.util.regex.Pattern
data class ParsedEpisode(val title:String,val season:Int?,val episode:Int)
object AnimeParser {
 private val ps=listOf(
  Pattern.compile("(?i)^(.*?)[ ._-]*S(\\d{1,2})E(\\d{1,4}).*\\.(mkv|mp4|webm)$"),
  Pattern.compile("(?i)^(.*?)[ ._-]+(?:EP?|Episode)[ ._-]?(\\d{1,4}).*\\.(mkv|mp4|webm)$"),
  Pattern.compile("(?i)^(.*?)[ ._-]+(\\d{1,4}).*\\.(mkv|mp4|webm)$"))
 fun parse(n:String):ParsedEpisode? {
  for((i,p) in ps.withIndex()){val m=p.matcher(n.trim());if(m.matches())return if(i==0)ParsedEpisode(m.group(1).trim(),m.group(2).toInt(),m.group(3).toInt())else ParsedEpisode(m.group(1).trim(),null,m.group(2).toInt())}
  return null
 }
}
