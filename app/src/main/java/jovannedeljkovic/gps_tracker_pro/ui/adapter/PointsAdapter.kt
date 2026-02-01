package jovannedeljkovic.gps_tracker_pro.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jovannedeljkovic.gps_tracker_pro.R
import jovannedeljkovic.gps_tracker_pro.data.entities.PointOfInterest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class PointsAdapter(
    private var points: List<PointOfInterest>,
    private val onItemClick: (PointOfInterest) -> Unit,
    private val onCheckboxChange: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<PointsAdapter.PointViewHolder>(), Filterable {

    private val selectedPositions = BooleanArray(points.size) { false }
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    // DODATO: Lista za filtrirane tačke
    private var filteredPoints: List<PointOfInterest> = points
    // DODATO: Mapiranje originalne pozicije -> filtrirana pozicija
    private val originalToFilteredMap = mutableMapOf<Int, Int>()
    private val filteredToOriginalMap = mutableMapOf<Int, Int>()

    inner class PointViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        val tvPointName: TextView = itemView.findViewById(R.id.tvPointName)
        val tvCoordinates: TextView = itemView.findViewById(R.id.tvCoordinates)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && position < filteredPoints.size) {
                    onItemClick(filteredPoints[position])
                }
            }

            checkbox.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && position < filteredPoints.size) {
                    // Pronađi originalnu poziciju
                    val originalPosition = filteredToOriginalMap[position]
                    originalPosition?.let {
                        selectedPositions[it] = isChecked
                        onCheckboxChange(it, isChecked)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PointViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_point, parent, false)
        return PointViewHolder(view)
    }

    override fun onBindViewHolder(holder: PointViewHolder, position: Int) {
        val point = filteredPoints[position]

        holder.tvPointName.text = point.name
        holder.tvCoordinates.text = String.format("%.6f, %.6f", point.latitude, point.longitude)
        holder.tvDate.text = dateFormat.format(Date(point.createdAt))

        // Pronađi originalnu poziciju i ažuriraj checkbox stanje
        val originalPosition = filteredToOriginalMap[position]
        holder.checkbox.isChecked = originalPosition?.let { selectedPositions[it] } ?: false

        // Ovo možete implementirati kasnije za računanje udaljenosti
        // holder.tvDistance.text = formatDistance(distance)
        // holder.tvDistance.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int = filteredPoints.size

    fun selectAll(select: Boolean) {
        for (i in selectedPositions.indices) {
            selectedPositions[i] = select
        }
        notifyDataSetChanged()
        // Obavesti da su sve promenjene
        onCheckboxChange(-1, select)
    }

    fun getSelectedPoints(): List<PointOfInterest> {
        return points.filterIndexed { index, _ -> selectedPositions[index] }
    }

    fun updatePoints(newPoints: List<PointOfInterest>) {
        val oldSize = points.size
        val newSize = newPoints.size

        points = newPoints

        if (oldSize != newSize) {
            // Kreiraj novi selectedPositions array
            val newSelectedPositions = BooleanArray(newSize) { false }
            // Kopiraj stare vrednosti ako postoje
            for (i in 0 until minOf(oldSize, newSize)) {
                if (i < selectedPositions.size) {
                    newSelectedPositions[i] = selectedPositions[i]
                }
            }
            // Koristi arraycopy da ažuriraš postojeći array
            if (newSelectedPositions.size <= selectedPositions.size) {
                System.arraycopy(newSelectedPositions, 0, selectedPositions, 0, newSelectedPositions.size)
            }
        }

        // Ažuriraj i filtrirane tačke
        filteredPoints = points
        rebuildPositionMaps()
        notifyDataSetChanged()
    }

    // ========== METODE ZA PRETRAGU ==========

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val filteredList = mutableListOf<PointOfInterest>()

                if (constraint.isNullOrBlank()) {
                    // Ako nema filtera, vrati sve tačke
                    filteredList.addAll(points)
                } else {
                    val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()

                    for (point in points) {
                        // Pretraga po imenu tačke
                        if (point.name.lowercase(Locale.getDefault()).contains(filterPattern)) {
                            filteredList.add(point)
                        }
                        // Možeš dodati i pretragu po drugim poljima ako želiš:
                        // if (point.description?.lowercase()?.contains(filterPattern) == true) {
                        //     filteredList.add(point)
                        // }
                    }
                }

                results.values = filteredList
                results.count = filteredList.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredPoints = results?.values as? List<PointOfInterest> ?: emptyList()
                rebuildPositionMaps()
                notifyDataSetChanged()
            }
        }
    }

    // Pomoćna metoda za rebuild mape pozicija
    private fun rebuildPositionMaps() {
        originalToFilteredMap.clear()
        filteredToOriginalMap.clear()

        for ((filteredIndex, point) in filteredPoints.withIndex()) {
            val originalIndex = points.indexOfFirst { it.id == point.id }
            if (originalIndex != -1) {
                originalToFilteredMap[originalIndex] = filteredIndex
                filteredToOriginalMap[filteredIndex] = originalIndex
            }
        }
    }

    // Nova metoda za dobijanje broja filtriranih tačaka
    fun getFilteredCount(): Int = filteredPoints.size

    // Nova metoda za resetovanje filtera
    fun resetFilter() {
        filteredPoints = points
        rebuildPositionMaps()
        notifyDataSetChanged()
    }

    // Dodajte ovu pomocnu metodu za resetovanje selektovanih pozicija
    private fun resetSelectedPositions() {
        for (i in selectedPositions.indices) {
            selectedPositions[i] = false
        }
    }
}