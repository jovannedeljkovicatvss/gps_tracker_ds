package jovannedeljkovic.gps_tracker_pro.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
) : RecyclerView.Adapter<PointsAdapter.PointViewHolder>() {

    private val selectedPositions = BooleanArray(points.size) { false }
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    inner class PointViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        val tvPointName: TextView = itemView.findViewById(R.id.tvPointName)
        val tvCoordinates: TextView = itemView.findViewById(R.id.tvCoordinates)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(points[position])
                }
            }

            checkbox.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    selectedPositions[position] = isChecked
                    onCheckboxChange(position, isChecked)
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
        val point = points[position]

        holder.tvPointName.text = point.name
        holder.tvCoordinates.text = String.format("%.6f, %.6f", point.latitude, point.longitude)
        holder.tvDate.text = dateFormat.format(Date(point.createdAt))

        // Ažuriraj checkbox stanje
        holder.checkbox.isChecked = selectedPositions[position]

        // Ovo možete implementirati kasnije za računanje udaljenosti
        // holder.tvDistance.text = formatDistance(distance)
        // holder.tvDistance.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int = points.size

    fun selectAll(select: Boolean) {
        for (i in selectedPositions.indices) {
            selectedPositions[i] = select
        }
        notifyDataSetChanged()
    }

    fun getSelectedPoints(): List<PointOfInterest> {
        return points.filterIndexed { index, _ -> selectedPositions[index] }
    }

    fun updatePoints(newPoints: List<PointOfInterest>) {
        // OVA LINIJA JE ISPRAVLJENA: ne reassign-ujemo points direktno
        val oldSize = points.size
        val newSize = newPoints.size

        // Ažuriraj listu
        points = newPoints

        // Resetuj selectedPositions array sa novom veličinom
        // OVO JE KLJUČNA ISPRAVKA:
        if (oldSize != newSize) {
            // Kreiraj novi array
            val newSelectedPositions = BooleanArray(newSize) { false }
            // Kopiraj stare vrednosti ako postoje
            for (i in 0 until minOf(oldSize, newSize)) {
                if (i < selectedPositions.size) {
                    newSelectedPositions[i] = selectedPositions[i]
                }
            }
            // Koristi novi array
            // Napomena: selectedPositions je val, ne možemo reassign
            // Zato koristimo drugi pristup:
            System.arraycopy(newSelectedPositions, 0, selectedPositions, 0, minOf(selectedPositions.size, newSelectedPositions.size))
        }

        notifyDataSetChanged()
    }

    // Dodajte ovu pomocnu metodu za resetovanje selektovanih pozicija
    private fun resetSelectedPositions() {
        for (i in selectedPositions.indices) {
            selectedPositions[i] = false
        }
    }
}