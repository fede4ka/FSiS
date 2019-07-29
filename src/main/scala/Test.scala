object Test extends App{

  import Functor._
  val FLO = Functor[List] compose Functor[Option]
val test:List[Option[Int]]= List(Some(3),Some(5),None)
println(FLO.map(test)(_+1))



  import Applicative._

val test1 = Applicative[List].map2(List(1,2,3),List(4,5,6))(_+_)
println(test1)

val AO = Applicative[Option]
  def addone(x:Int):Int = x + 1
val test2 = AO.apply(Some(2))(AO.pure(addone(_)))
println(test2)

}
