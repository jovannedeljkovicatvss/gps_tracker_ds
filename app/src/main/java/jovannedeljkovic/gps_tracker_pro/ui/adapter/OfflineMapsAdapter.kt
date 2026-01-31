package jovannedeljkovic.gps_tracker_pro.ui.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import jovannedeljkovic.gps_tracker_pro.R
import jovannedeljkovic.gps_tracker_pro.data.model.OfflineMapItem

class OfflineMapsAdapter(
    private val maps: List<OfflineMapItem>,
    private val onMapClick: (OfflineMapItem) -> Unit,
    private val onDeleteClick: (OfflineMapItem) -> Unit,
    private val onRenameClick: (OfflineMapItem) -> Unit
) : RecyclerView.Adapter<OfflineMapsAdapter.OfflineMapViewHolder>() {

    inner class OfflineMapViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMapName: TextView = itemView.findViewById(R.id.tvMapName)
        val tvAdditionalInfo: TextView = itemView.findViewById(R.id.tvAdditionalInfo)
        val tvTileCount: TextView = itemView.findViewById(R.id.tvTileCount)
        val tvIcon: TextView = itemView.findViewById(R.id.tvIcon)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)  // <-- SADA JE Button
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
        holder.tvAdditionalInfo.text = map.additionalInfo
        holder.tvTileCount.text = map.tileCount.toString()

        // KRATKI KLIK - aktivira mapu
        holder.itemView.setOnClickListener {
            onMapClick(map)
        }

        // DUGI KLIK - promeni ime (OVO JE KLJUČNO)
        holder.itemView.setOnLongClickListener {
            onRenameClick(map)  // OVO POZIVA RENAME FUNKCIJU
            true  // VAŽNO: vrati true da bi se dugi klik registrovao
        }

        // KLIK NA DELETE DUGME
        holder.btnDelete.setOnClickListener {
            onDeleteClick(map)
        }
    }


    // Dodajte ovu funkciju u adapter
    private fun showRenameDialog(map: OfflineMapItem, position: Int) {
        // Ovo ćemo implementirati u MainActivity
    }

    override fun getItemCount(): Int = maps.size
}