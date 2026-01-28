package jovannedeljkovic.gps_tracker_pro.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import jovannedeljkovic.gps_tracker_pro.R
import jovannedeljkovic.gps_tracker_pro.data.entities.User
import jovannedeljkovic.gps_tracker_pro.utils.FeatureManager
import java.text.SimpleDateFormat
import java.util.*

class UsersAdapter(
    private val users: List<User>,
    private val onUserClick: (User) -> Unit
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEmail: TextView = itemView.findViewById(R.id.tvUserEmail)
        private val tvRole: TextView = itemView.findViewById(R.id.tvUserRole)
        private val tvDate: TextView = itemView.findViewById(R.id.tvUserDate)

        fun bind(user: User) {
            tvEmail.text = user.email
            tvRole.text = FeatureManager.getUserRoleDisplayName(user)

            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            tvDate.text = dateFormat.format(Date(user.createdAt))

            // Različite boje za različite uloge
            when (user.role) {
                "ADMIN" -> tvRole.setTextColor(ContextCompat.getColor(itemView.context, R.color.accent_red))
                "PREMIUM" -> tvRole.setTextColor(ContextCompat.getColor(itemView.context, R.color.accent_green))
                else -> tvRole.setTextColor(ContextCompat.getColor(itemView.context, R.color.blue_primary))
            }

            itemView.setOnClickListener {
                onUserClick(user)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size
}