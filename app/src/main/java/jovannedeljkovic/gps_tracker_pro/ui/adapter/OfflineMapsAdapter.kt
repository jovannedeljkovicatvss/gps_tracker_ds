package jovannedeljkovic.gps_tracker_pro.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jovannedeljkovic.gps_tracker_pro.R
import jovannedeljkovic.gps_tracker_pro.data.model.OfflineMapItem

// Možete definisati ovu klasu ovde ili u MainActivity


class OfflineMapsAdapter(
    private val maps: List<OfflineMapItem>,  // Isto iz data.model
    private val onMapClick: (OfflineMapItem) -> Unit
) : RecyclerView.Adapter<OfflineMapsAdapter.OfflineMapViewHolder>() {

    inner class OfflineMapViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMapName: TextView = itemView.findViewById(R.id.tvMapName)
        val tvTileCount: TextView = itemView.findViewById(R.id.tvTileCount)
        val tvIcon: TextView = itemView.findViewById(R.id.tvIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OfflineMapViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_offline_map, parent, false)
        return OfflineMapViewHolder(view)
    }

    override fun onBindViewHolder(holder: OfflineMapViewHolder, position: Int) {
        val map = maps[position]

        holder.tvIcon.text = map.icon
        holder.tvMapName.text = map.name
        holder.tvTileCount.text = "${map.tileCount} tile-ova"

        holder.itemView.setOnClickListener {
            onMapClick(map)
        }
    }

    override fun getItemCount(): Int = maps.size
}