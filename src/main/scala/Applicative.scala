
trait Applicative[F[_]] extends Functor[F] { self =>

  // Example: take 1 and lift into List[Int](1)
  // Called pure because it takes a raw, "pure" value that exists outside of
  // "effect system" and lifts into effect system. These are not side-effects,
  // rather that, for example, Option models the "effect" of having or not
  // having a value. List models the "effect" of having multiple values.
  def pure[A](a: A): F[A]

  // Takes two proper types, A and B, and an F[A], but instead of taking A => B,
  // as with Functor's map, takes a type that exists _within_ the type
  // constructor. Applicative operates inside the "container", Functor "unwraps"
  // and "rewraps".
  def apply[A, B](fa: F[A])(ff: F[A => B]): F[B]

  /* Derived methods */

  def apply2[A, B, Z](fa: F[A], fb: F[B])(ff: F[(A, B) => Z]): F[Z] =
    apply(fa)(apply(fb)(map(ff)(f => b => a => f(a,b))))

  // Map is just apply but with function not wrapped in type constructor F.
  override def map[A, B](fa: F[A])(f: A => B): F[B] =
    apply(fa)(pure(f))

  def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
    apply(fa)(map(fb)(b => f(_,b))) // or: apply(fb)(map(fa)(f.curried))

  def map3[A, B, C, Z](fa: F[A], fb: F[B], fc: F[C])(f: (A, B, C) => Z): F[Z] =
    apply(fa)(map2(fb, fc)((b, c) => f(_, b, c))) // or: apply(fa)(map2(fb, fc)(a => f(a, b, c)))

  def map4[A, B, C, D, Z](fa: F[A], fb: F[B], fc: F[C], fd: F[D])(f: (A, B, C, D) => Z): F[Z] =
    map2(tuple2(fa, fb), tuple2(fc, fd)) { case ((a, b), (c,d)) => f(a, b, c, d) }

  def tuple2[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
    map2(fa, fb)((a, b) => (a, b))

  def tuple3[A, B, C](fa: F[A], fb: F[B], fc: F[C]): F[(A, B, C)] =
    map3(fa, fb, fc)((a, b, c) => (a, b, c))


  def flip[A, B](ff: F[A => B]): F[A] => F[B] = fa => apply(fa)(ff)

  def compose[G[_]](implicit G: Applicative[G]): Applicative[Lambda[X => F[G[X]]]] =
   new Applicative[Lambda[X => F[G[X]]]] {
      def pure[A](a: A): F[G[A]] = self.pure(G.pure(a))

      def apply[A, B](fga: F[G[A]])(ff: F[G[A => B]]): F[G[B]] = {
        // We have F[G[A]], so if we had a F[G[A] => G[B]], we could simply use
        // F's apply with that function. Thus we unpack ff to give us a function
        // gab: G[A => B] and call G.flip on it to yield G[A] => G[B]
        val x: F[G[A] => G[B]] = self.map(ff)(G.flip)
        self.apply(fga)(x)
      }
    }


}

object Applicative {


  def apply[F[_]](implicit instance: Applicative[F]): Applicative[F] = instance


  trait Ops[F[_]] {
    def typeClassInstance: Applicative[F]
    def self: F[_]
  }

  trait ToApplicativeOps {
    implicit def toApplicativeOps[F[_]](target: F[_])(implicit tc: Applicative[F]): Ops[F] = new Ops[F] {
      val self = target
      val typeClassInstance = tc
    }
  }
  object nonInheritedOps extends ToApplicativeOps

  trait AllOps[F[_]] extends Ops[F] {
    def typeClassInstance: Applicative[F]
  }

  object ops {
    implicit def toAllApplicativeOps[F[_]](target: F[_])(implicit tc: Applicative[F]): AllOps[F] = new AllOps[F] {
      val self = target
      val typeClassInstance = tc
    }
  }



  implicit val optionApplicative: Applicative[Option] = new Applicative[Option] {
    def pure[A](a: A): Option[A] = Some(a)

    def apply[A, B](fa: Option[A])(ff: Option[A => B]): Option[B] = (fa, ff) match {
      case (None, _) => None
      case (Some(a), None) => None
      case (Some(a), Some(f)) => Some(f(a))
    }
  }

  implicit val listApplicative: Applicative[List] = new Applicative[List] {
    def pure[A](a: A): List[A] = List(a)
    def apply[A, B](fa: List[A])(ff: List[A => B]): List[B] = for {
      a <- fa
      f <- ff
    } yield f(a)
  }

  implicit val streamApplicative: Applicative[LazyList] = new Applicative[LazyList] {
    def pure[A](a: A) = LazyList.continually(a)
    // If pure returned a singleton stream, Stream(a), this apply would not be
    // lawful, because applying, for example, Stream(identity) to Stream(1,2,3)
    // would return Stream(1), not the original stream. By making pure an
    // infinite stream, this implementation becomes lawful.

    // This only works for Stream in scala, because it can be infinite. In
    // Haskell, a list is lazily evaluated, so Haskell has list applicatives for
    // both the cross-product (above) and this zip apply.
    def apply[A, B](fa: LazyList[A])(ff: LazyList[A => B]): LazyList[B] =
      (fa zip ff) map { case (a, f) => f(a) }
  }
}

trait ApplicativeLaws[F[_]] {

  import IsEq._

  implicit def F: Applicative[F]

  def applicativeIdentity[A](fa: F[A])(implicit F: Applicative[F]): IsEq[F[A]] =
   F.apply(fa)(F.pure((a: A) => a))  =?= fa

  // Result of lifting A and applying lifted A => B must match the result of
  // directly applying the A => B to A and _then_ lifting it into context.
  // Function application "distributes over" apply and pure.
  def applicativeHomomorphism[A, B](a: A, f: A => B)  =
    F.apply(F.pure(a))(F.pure(f)) =?= F.pure(f(a))

  // Lifting A and applying ff to it gives the same F[B] as lifting a function
  // A => B that returns f(a) and applying it to ff. In the second case we
  // lift an (A => B) => B, then apply it to F[A => B] to give F[B].
  def applicativeInterchage[A, B](a: A, ff: F[A => B]) =
    F.apply(F.pure(a))(ff) =?= F.apply(ff)(F.pure((f: A => B) => f(a)))
    //F.pure(a).apply(ff) =?= ff.apply(F.pure((f: A => B) => f(a)))

  // Map operation must be consistent with apply and pure.
  // Mapping over fa with pure function f must be equal to applying the a lifted
  // f.
  def applicativeMap[A, B](fa: F[A], f: A => B) =
    F.map(fa)(f) =?= F.apply(fa)(F.pure(f))
    //fa.map(f) =?= fa.apply(F.pure(f))
}

object ApplicativeLaws {
  def apply[F[_]](implicit F0: Applicative[F]): ApplicativeLaws[F] = new ApplicativeLaws[F] {
    def F = F0
  }
}
