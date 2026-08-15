package com.example.vlcmasync
import okhttp3.*
import org.json.JSONObject
import java.net.URLEncoder
data class MalAnime(val id:Int,val title:String)
class MalApi(private val c:OkHttpClient=OkHttpClient()){
 private val api="https://api.myanimelist.net/v2"
 fun token(cid:String,code:String,v:String,r:String):String{
  val b=FormBody.Builder().add("client_id",cid).add("code",code).add("code_verifier",v).add("grant_type","authorization_code").add("redirect_uri",r).build()
  c.newCall(Request.Builder().url("https://myanimelist.net/v1/oauth2/token").post(b).build()).execute().use{val t=it.body?.string().orEmpty();if(!it.isSuccessful)error(t);return JSONObject(t).getString("access_token")}
 }
 fun search(t:String,q:String):List<MalAnime>{
  val u="$api/anime?q=${URLEncoder.encode(q,"UTF-8")}&limit=10&fields=id,title"
  c.newCall(Request.Builder().url(u).header("Authorization","Bearer $t").build()).execute().use{val s=it.body?.string().orEmpty();if(!it.isSuccessful)error(s);val a=JSONObject(s).getJSONArray("data");return(0 until a.length()).map{val n=a.getJSONObject(it).getJSONObject("node");MalAnime(n.getInt("id"),n.getString("title"))}}
 }
 fun update(t:String,id:Int,n:Int){
  val b=FormBody.Builder().add("num_watched_episodes",n.toString()).build()
  c.newCall(Request.Builder().url("$api/anime/$id/my_list_status").header("Authorization","Bearer $t").put(b).build()).execute().use{if(!it.isSuccessful)error(it.body?.string().orEmpty())}
 }
}
