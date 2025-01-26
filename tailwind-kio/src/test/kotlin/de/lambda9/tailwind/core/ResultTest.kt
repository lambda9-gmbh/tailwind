package de.lambda9.tailwind.core



class ResultTest {

    private fun <T> id(it: T) = it


    /**
     * Checks whether the function actually maps.
     *
     * ```haskell
     * map id == id
     * ```
     */
    //@Property
    //fun <E, A> checkIdentityLaw(@ForAll result: Result<E, A>) {
    //    result map { id(it) } == id(result)
    //}


    /**
     * Checks whether the function simplifies.
     *
     * ```haskell
     * map (f . g) == map f . map g
     * ```
     */
    //@Property
    //@Disabled
    //fun <E, A> checkCompositionLaw(@ForAll result: Result<E, A>) {
    //    //result.map { g.ma } map g map f == result map { f(g(it)) }
    //}

}