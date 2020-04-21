package ee.taltech.spormapsapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.recycler_row_category.view.*

class DataRecyclerViewAdapterCategories(
    context: Context,
    repo: LocationCategoryRepository
) : RecyclerView.Adapter<DataRecyclerViewAdapterCategories.ViewHolder>() {

    var dataSet: List<LocationCategory> = repo.getAll()

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val rowView = inflater.inflate(R.layout.recycler_row_category, parent, false)
        return ViewHolder(rowView)
    }

    override fun getItemCount(): Int {
        return dataSet.count()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = dataSet.get(position)
        holder.itemView.textViewId.text = category.id.toString()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
