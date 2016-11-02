package info.whereismyfood.aux

/**
  * Created by zakgoichman on 11/2/16.
  */
trait ReflectionCompanionObject {
  import scala.reflect.runtime.{universe => ru}
  private lazy val universeMirror = ru.runtimeMirror(getClass.getClassLoader)

  def companionOf[T](implicit tt: ru.TypeTag[T])  = {
    val companionMirror = universeMirror.reflectModule(ru.typeOf[T].typeSymbol.companionSymbol.asModule)
    companionMirror.instance
  }
}
