package jovannedeljkovic.gps_tracker_pro.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jovannedeljkovic.gps_tracker_pro.R
import jovannedeljkovic.gps_tracker_pro.data.entities.Route
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class RoutesAdapter(
    private var routes: List<Route>,
    private val onRouteClick: (Route) -> Unit,
    private val onShowOnMap: (Route) -> Unit,
    private val onExportRoute: (Route) -> Unit,
    private val onDeleteRoute: (Route) -> Unit
) : RecyclerView.Adapter<RoutesAdapter.RouteViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private var isMultiSelectMode = false
    private val selectedPositions = BooleanArray(routes.size) { false }

    inner class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        val tvRouteName: TextView = itemView.findViewById(R.id.tvRouteName)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val btnShowOnMap: Button = itemView.findViewById(R.id.btnShowOnMap)
        val btnExportRoute: Button = itemView.findViewById(R.id.btnExportRoute)
        val btnDeleteRoute: Button = itemView.findViewById(R.id.btnDeleteRoute)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    if (isMultiSelectMode) {
                        // U multi-select modu, toggle checkbox
                        selectedPositions[position] = !selectedPositions[position]
                        checkbox.isChecked = selectedPositions[position]
                        notifyItemChanged(position)
                    } else {
                        // Normalan klik - otvori detalje
                        onRouteClick(routes[position])
                    }
                }
            }

            checkbox.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    selectedPositions[position] = isChecked
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]

        // Prikaži/sakrij checkbox u zavisnosti od moda
        holder.checkbox.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
        holder.checkbox.isChecked = selectedPositions[position]

        // Sakrij dugmiće akcija u multi-select modu
        val actionVisibility = if (isMultiSelectMode) View.GONE else View.VISIBLE
        holder.btnShowOnMap.visibility = actionVisibility
        holder.btnExportRoute.visibility = actionVisibility
        holder.btnDeleteRoute.visibility = actionVisibility

        // Bindovanje podataka
        holder.tvRouteName.text = route.name

        // Status
        holder.tvStatus.text = if (route.isCompleted) "✅" else "🟡"
        holder.tvStatus.background = null

        // Distance
        val distanceKm = route.distance / 1000.0
        holder.tvDistance.text = if (distanceKm < 1) {
            "${route.distance.roundToInt()} m"
        } else {
            String.format("%.2f km", distanceKm)
        }

        // Time
        val minutes = (route.duration / 1000 / 60).toInt()
        holder.tvTime.text = if (minutes < 60) {
            "$minutes min"
        } else {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            "${hours}h ${remainingMinutes}min"
        }

        // Date
        holder.tvDate.text = dateFormat.format(Date(route.startTime))

        // Button listeners (samo ako nije multi-select mode)
        if (!isMultiSelectMode) {
            holder.btnShowOnMap.setOnClickListener {
                onShowOnMap(route)
            }

            holder.btnExportRoute.setOnClickListener {
                onExportRoute(route)
            }

            holder.btnDeleteRoute.setOnClickListener {
                onDeleteRoute(route)
            }
        }
    }

    override fun getItemCount(): Int = routes.size

    // Metode za multiple selection
    fun setMultiSelectMode(enabled: Boolean) {
        isMultiSelectMode = enabled
        if (!enabled) {
            // Resetuj selektovane kada se isključi multi-select
            for (i in selectedPositions.indices) {
                selectedPositions[i] = false
            }
        }
        notifyDataSetChanged()
    }

    fun isMultiSelectMode(): Boolean = isMultiSelectMode

    fun selectAll(select: Boolean) {
        for (i in selectedPositions.indices) {
            selectedPositions[i] = select
        }
        notifyDataSetChanged()
    }

    fun getSelectedRoutes(): List<Route> {
        return routes.filterIndexed { index, _ -> selectedPositions[index] }
    }

    fun getSelectedCount(): Int {
        return selectedPositions.count { it }
    }

    fun updateRoutes(newRoutes: List<Route>) {
        // OVO JE ISPRAVLJENO: ne reassign-ujemo direktno
        val oldList = routes
        routes = newRoutes

        // Kreiraj novi array ako se promenila veličina
        if (oldList.size != newRoutes.size) {
            // selectedPositions je val, moramo koristiti drugi pristup
            // Resetujemo selektovane pozicije
            for (i in selectedPositions.indices) {
                if (i < newRoutes.size) {
                    // Ostavi postojeće vrednosti za postojeće pozicije
                    if (i >= oldList.size) {
                        selectedPositions[i] = false
                    }
                }
            }
        }

        notifyDataSetChanged()
    }

    fun calculateTotalDistance(): Double {
        return routes.sumOf { it.distance }
    }

    fun calculateTotalTime(): Long {
        return routes.sumOf { it.duration }
    }
}