import Functor._
val FLO = Functor[List] compose Functor[Option]
val test:List[Option[Int]]= List(Some(3),Some(5),None)
print(FLO.map(test)(_+1))