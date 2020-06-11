package eu.vaadinonkotlin.vaadin10

import com.github.mvysny.vokdataloader.DataLoader
import com.github.mvysny.vokdataloader.Filter
import com.github.mvysny.vokdataloader.ListDataLoader
import com.github.mvysny.vokdataloader.SortClause
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.data.provider.AbstractBackEndDataProvider
import com.vaadin.flow.data.provider.DataProvider
import com.vaadin.flow.data.provider.Query
import com.vaadin.flow.data.provider.SortDirection
import java.util.stream.Stream

/**
 * A Vaadin [DataProvider] implementation which delegates the data-fetching calls to a VoK-ORM [DataLoader];
 * an adapter which adapts calls to Vaadin DataProvider to VoK-ORM DataLoader.
 * @param T the type of items returned by this data provider.
 * @param loader performs the actual data fetching. All data-fetching calls are delegated here.
 * @param idResolver provides unique ID for every item. The ID is then used to differentiate items.
 * See [DataProvider.getId] for more details. Typically every item
 * has a primary key of type [Long], but any Java/Kotlin object with properly written [Any.equals] and [Any.hashCode] can act as the ID,
 * including the item itself.
 */
class DataLoaderAdapter<T : Any>(private val loader: DataLoader<T>, private val idResolver: (T)->Any)
        : AbstractBackEndDataProvider<T, Filter<T>?>() {
    override fun getId(item: T): Any = idResolver(item)
    override fun toString() = "DataLoaderAdapter($loader)"
    override fun sizeInBackEnd(query: Query<T, Filter<T>?>?): Int {
        val count: Long = loader.getCount(query?.filter?.orElse(null))
        return count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    override fun fetchFromBackEnd(query: Query<T, Filter<T>?>?): Stream<T> {
        val sortBy: List<SortClause> = query?.sortOrders?.map {
            SortClause(it.sorted, it.direction == SortDirection.ASCENDING)
        } ?: listOf()
        val offset = query?.offset ?: 0
        val limit = query?.limit ?: Int.MAX_VALUE
        var endInclusive: Int = (limit.toLong() + offset - 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (endInclusive == Int.MAX_VALUE - 1) endInclusive = Int.MAX_VALUE
        val list: List<T> = loader.fetch(query?.filter?.orElse(null), sortBy, offset.toLong()..endInclusive.toLong())
        return list.stream()
    }
}

/**
 * Adapts [DataLoader] to Vaadin's [VokDataProvider].
 * @param idResolver provides unique ID for every item. The ID is then used to differentiate items.
 * See [DataProvider.getId] for more details. Typically every item
 * has a primary key of type [Long], but any Java/Kotlin object with properly written [Any.equals] and [Any.hashCode] can act as the ID,
 * including the item itself.
 */
fun <T: Any> DataLoader<T>.asDataProvider(idResolver: (T) -> Any): VokDataProvider<T> =
        DataLoaderAdapter(this, idResolver).withConfigurableFilter2()

/**
 * Sets given data loader to this Grid, by the means of wrapping the data loader via [DataLoaderAdapter] and setting it
 * as a (configurable) [Grid.getDataProvider].
 * @param idResolver provides unique ID for every item. The ID is then used to differentiate items.
 * See [com.vaadin.flow.data.provider.DataProvider.getId] for more details. Typically every item
 * has a primary key of type [Long], but any Java/Kotlin object with properly written [Any.equals] and [Any.hashCode] can act as the ID,
 * including the item itself.
 */
fun <T: Any> Grid<T>.setDataLoader(dataLoader: DataLoader<T>, idResolver: (T)->Any) {
    dataProvider = dataLoader.asDataProvider(idResolver)
}

/**
 * An utility method to set [ListDataLoader] to the Grid.
 */
inline fun <reified T: Any> Grid<T>.setDataLoaderItems(items: List<T>, noinline idResolver: (T) -> Any = { it }) {
    setDataLoader(ListDataLoader(T::class.java, items), idResolver)
}

/**
 * An utility method to set [ListDataLoader] to the Grid.
 */
inline fun <reified T: Any> Grid<T>.setDataLoaderItems(vararg items: T, noinline idResolver: (T) -> Any = { it }) {
    setDataLoaderItems(items.toList(), idResolver)
}
