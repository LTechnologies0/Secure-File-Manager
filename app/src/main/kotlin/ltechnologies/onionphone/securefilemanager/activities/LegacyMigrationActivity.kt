package ltechnologies.onionphone.securefilemanager.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ltechnologies.onionphone.securefilemanager.R
import ltechnologies.onionphone.securefilemanager.databinding.ActivityLegacyMigrationBinding
import ltechnologies.onionphone.securefilemanager.databinding.ItemLegacyAesBinding
import ltechnologies.onionphone.securefilemanager.dialogs.ConfirmationDialog
import ltechnologies.onionphone.securefilemanager.extensions.beGone
import ltechnologies.onionphone.securefilemanager.extensions.beVisible
import ltechnologies.onionphone.securefilemanager.extensions.beVisibleIf
import ltechnologies.onionphone.securefilemanager.extensions.formatSize
import ltechnologies.onionphone.securefilemanager.extensions.getInternalStoragePath
import ltechnologies.onionphone.securefilemanager.extensions.humanizePath
import ltechnologies.onionphone.securefilemanager.extensions.toast
import ltechnologies.onionphone.securefilemanager.helpers.encryptionExtensionDotted
import ltechnologies.onionphone.securefilemanager.helpers.ensureBackgroundThread
import java.io.File

class LegacyMigrationActivity : BaseAbstractActivity() {
    private lateinit var binding: ActivityLegacyMigrationBinding
    private var aesFiles = listOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLegacyMigrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.decryptAllButton.setOnClickListener {
            toast(R.string.legacy_migration_decrypt_unavailable)
        }
        binding.encryptWithOkcButton.setOnClickListener {
            toast(R.string.legacy_migration_decrypt_unavailable)
        }
        binding.deleteRemainingButton.setOnClickListener { confirmDelete() }
        scan()
    }

    private fun scan() {
        binding.progressBar.beVisible()
        ensureBackgroundThread {
            val root = File(getInternalStoragePath())
            val found = mutableListOf<File>()
            scanAes(root, found)
            aesFiles = found.sortedBy { it.absolutePath }
            runOnUiThread {
                binding.progressBar.beGone()
                bindList()
            }
        }
    }

    private fun scanAes(dir: File, out: MutableList<File>) {
        dir.listFiles()?.forEach { child ->
            when {
                child.isDirectory && !child.name.startsWith(".") -> scanAes(child, out)
                child.isFile && child.name.endsWith(encryptionExtensionDotted, ignoreCase = true) ->
                    out.add(child)
            }
        }
    }

    private fun bindList() {
        binding.summaryText.text = if (aesFiles.isEmpty()) {
            getString(R.string.legacy_migration_empty)
        } else {
            getString(R.string.legacy_migration_summary, aesFiles.size)
        }
        binding.emptyPlaceholder.beVisibleIf(aesFiles.isEmpty())
        binding.legacyAesList.beVisibleIf(aesFiles.isNotEmpty())
        binding.deleteRemainingButton.isEnabled = aesFiles.isNotEmpty()
        binding.decryptAllButton.isEnabled = aesFiles.isNotEmpty()
        binding.encryptWithOkcButton.isEnabled = false
        binding.legacyAesList.layoutManager = LinearLayoutManager(this)
        binding.legacyAesList.adapter = AesAdapter(aesFiles)
    }

    private fun confirmDelete() {
        if (aesFiles.isEmpty()) {
            return
        }
        ConfirmationDialog(this, getString(R.string.legacy_migration_delete_confirm)) {
            ensureBackgroundThread {
                aesFiles.forEach { it.delete() }
                runOnUiThread { scan() }
            }
        }
    }

    private class AesAdapter(private val files: List<File>) :
        RecyclerView.Adapter<AesAdapter.Holder>() {
        class Holder(val binding: ItemLegacyAesBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemLegacyAesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(binding)
        }

        override fun getItemCount() = files.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val file = files[position]
            holder.binding.legacyAesName.text = file.name
            holder.binding.legacyAesPath.text = holder.itemView.context.humanizePath(file.parent ?: "")
            holder.binding.legacyAesSize.text = file.length().formatSize()
        }
    }

    companion object {
        fun start(activity: BaseAbstractActivity) {
            activity.startActivity(Intent(activity, LegacyMigrationActivity::class.java))
        }
    }
}
