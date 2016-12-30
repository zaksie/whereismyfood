package info.whereismyfood.routes.api.v1.http.templates

/**
  * Created by zakgoichman on 12/26/16.
  */
object MenuTemplate {
  def menuTemplate: String = Common.loadTemplate("menu-template.html")
}
case class MenuTemplate(id: Long){
  import MenuTemplate._
  private val idd = 1032294585 //TODO: remove this
  private val menuUrl = s"https://storage.googleapis.com/whereismyfood/businesses/$idd/menu-2.pdf"
  def html: String = menuTemplate.replace("%MENU_URL%", menuUrl)
}
