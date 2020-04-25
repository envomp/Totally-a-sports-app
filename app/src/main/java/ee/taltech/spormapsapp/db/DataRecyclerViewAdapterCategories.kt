package ee.taltech.spormapsapp.db

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import ee.taltech.spormapsapp.LocationService
import ee.taltech.spormapsapp.R
import ee.taltech.spormapsapp.helper.C
import ee.taltech.spormapsapp.helper.StateVariables
import kotlinx.android.synthetic.main.recycler_row_category.view.*
import kotlinx.android.synthetic.main.recycler_row_category.view.categoryTrueHash
import java.lang.Math.abs
import java.sql.Date
import kotlin.random.Random


class DataRecyclerViewAdapterCategories(
    private val context: Context,
    private val repo: LocationCategoryRepository
) : RecyclerView.Adapter<DataRecyclerViewAdapterCategories.ViewHolder>() {

    //    private var locations: List<LocationCategory> = repo.getAllLocations()
    private var aliases: List<LocationAlias> = repo.getAllAliases()
    private val inflater: LayoutInflater = LayoutInflater.from(context)


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val rowView = inflater.inflate(R.layout.recycler_row_category, parent, false)
        return ViewHolder(rowView, repo, context)
    }

    override fun getItemCount(): Int {
        return aliases.count()
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = aliases[position]
        holder.itemView.categoryTrueHash.text = category.hash
        holder.itemView.categoryHash.text = "Name: ${category.alias}"
        holder.itemView.categoryLocations.text = "Locations: ${category.locations}"
        holder.itemView.categoryDate.text =
            "Date: ${Date((category.firstDate).toLong())}"
    }

    inner class ViewHolder(
        itemView: View,
        databaseConnector: LocationCategoryRepository,
        context: Context
    ) : RecyclerView.ViewHolder(itemView) {
        init {

            itemView.findViewById<Button>(R.id.delete_session).setOnClickListener {

                val builder: AlertDialog.Builder = AlertDialog.Builder(context)
                builder.setTitle("Are you sure you want to delete selected session?")

                builder.setPositiveButton(
                    "Delete"
                ) { _, _ ->
                    run {

                        val categoryTrueHash =
                            itemView.findViewById<TextView>(R.id.categoryTrueHash).text.toString()
                        databaseConnector.deleteByHash(categoryTrueHash)

                        val intent = Intent(C.DB_UPDATE)
                        PendingIntent.getBroadcast(context, 0, intent, 0).send()

                    }
                }

                builder.setNegativeButton(
                    "Cancel"
                ) { dialog, _ ->
                    run {
                        dialog.cancel()
                    }
                }

                builder.show()

            }

            itemView.findViewById<Button>(R.id.display_session).setOnClickListener {
                val categoryTrueHash =
                    itemView.findViewById<TextView>(R.id.categoryTrueHash).text.toString()
                val intent = Intent(C.DISPLAY_SESSION)
                intent.putExtra(C.DISPLAY_SESSION_HASH, categoryTrueHash)
                PendingIntent.getBroadcast(context, 0, intent, Random.nextInt(0, 1000)).send()
            }

            itemView.findViewById<Button>(R.id.rename_session).setOnClickListener {
                val categoryTrueHash =
                    itemView.findViewById<TextView>(R.id.categoryTrueHash).text.toString()
                val builder: AlertDialog.Builder = AlertDialog.Builder(context)
                builder.setTitle("Please enter a name for your session or email for import")

                val input = EditText(context)

                input.inputType = InputType.TYPE_CLASS_TEXT
                builder.setView(input)

                builder.setPositiveButton(
                    "Set alias"
                ) { _, _ ->
                    run {
                        databaseConnector.setAlias(
                            categoryTrueHash,
                            input.text.toString()
                        )

                        val intent = Intent(C.DB_UPDATE)
                        PendingIntent.getBroadcast(context, 0, intent, 0).send()

                    }
                }

                builder.setNegativeButton(
                    "Cancel"
                ) { dialog, _ ->
                    run {
                        dialog.cancel()
                    }
                }

                builder.setNeutralButton(
                    "Import"
                ) { dialog, _ ->
                    run {
                        dialog.cancel()

                        val intent = Intent(C.EXPORT_DB)
                        intent.putExtra(C.DISPLAY_SESSION_HASH, categoryTrueHash)
                        intent.putExtra(C.EXPORT_TO_EMAIL, input.text.toString())
                        PendingIntent.getBroadcast(context, 0, intent, Random.nextInt(0, 1000))
                            .send()
                    }
                }

                builder.show()
            }
        }
    }
}
