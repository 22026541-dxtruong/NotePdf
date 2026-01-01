package ie.app.notepdf.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ie.app.notepdf.R
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.Folder
import ie.app.notepdf.data.local.relation.FolderWithSub

@Composable
fun HomeGridLayout(
    folderWithSub: FolderWithSub? = null,
    enterFolder: (Long, String) -> Unit,
    deleteFolder: (Folder) -> Unit = {},
    editFolderName: (Folder) -> Unit = {},
    moveFolder: (Folder) -> Unit = {},
    deleteFile: (Document) -> Unit = {},
    editFileName: (Document) -> Unit = {},
    moveFile: (Document) -> Unit = {},
    shareFile: (Document) -> Unit = {},
    openFile: (Document) -> Unit = {}
) {
    folderWithSub?.let {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(it.subFolders, key = { folder -> "folder-${folder.id}" }) { folder ->
                GridFolderItem(
                    folder,
                    enterFolder,
                    deleteFolder,
                    moveFolder,
                    editFolderName,
                    modifier = Modifier
                        .width(120.dp)
                        .height(160.dp)
                        .animateItem()
                )
            }
            items(it.subDocuments, key = { pdf -> "file-${pdf.id}" }) { pdf ->
                GridFileItem(
                    pdf,
                    deleteFile,
                    editFileName,
                    moveFile,
                    shareFile,
                    openFile,
                    modifier = Modifier
                        .width(120.dp)
                        .height(160.dp)
                        .animateItem()
                )
            }
        }
    }
}

@Composable
fun GridFolderItem(
    folder: Folder,
    enterFolder: (Long, String) -> Unit,
    deleteFolder: (Folder) -> Unit = {},
    moveFolder: (Folder) -> Unit = {},
    editFolderName: (Folder) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { enterFolder(folder.id, folder.name) },
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.baseline_folder_48),
                contentDescription = "Folder",
                modifier = Modifier.padding(8.dp)
                    .size(24.dp)
            )
            Text(
                folder.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                expanded = !expanded
            }) {
                Icon(
                    painter = painterResource(R.drawable.outline_more_vert_24),
                    contentDescription = "More"
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Đổi tên") },
                    onClick = { editFolderName(folder) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.outline_edit_square_24),
                            contentDescription = "Edit Name"
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Di chuyển") },
                    onClick = { moveFolder(folder) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.outline_drive_file_move_24),
                            contentDescription = "Move"
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Xóa") },
                    onClick = { deleteFolder(folder) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.outline_delete_24),
                            contentDescription = "Delete"
                        )
                    }
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.baseline_folder_48),
            contentDescription = "Folder",
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun GridFileItem(
    pdf: Document,
    deleteFile: (Document) -> Unit = {},
    editFileName: (Document) -> Unit = {},
    moveFile: (Document) -> Unit = {},
    shareFile: (Document) -> Unit = {},
    openFile: (Document) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { openFile(pdf) },
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.baseline_picture_as_pdf_24),
                contentDescription = "Pdf",
                modifier = Modifier.padding(8.dp)
            )
            Text(
                pdf.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                expanded = !expanded
            }) {
                Icon(
                    painter = painterResource(R.drawable.outline_more_vert_24),
                    contentDescription = "More"
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Đổi tên") },
                    onClick = { editFileName(pdf) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.outline_edit_square_24),
                            contentDescription = "Edit Name"
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Di chuyển") },
                    onClick = { moveFile(pdf) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.outline_drive_file_move_24),
                            contentDescription = "Move"
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Chia sẻ") },
                    onClick = { shareFile(pdf) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.baseline_share_24),
                            contentDescription = "Share"
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Xóa") },
                    onClick = { deleteFile(pdf) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.outline_delete_24),
                            contentDescription = "Delete"
                        )
                    }
                )
            }
        }
        AsyncImage(
            model = pdf.thumbnailPath,
            contentDescription = "Pdf",
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.baseline_picture_as_pdf_24),
            error = painterResource(R.drawable.baseline_picture_as_pdf_24),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(8.dp))
                .align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun FolderListGrid(
    folders: List<Folder>,
    movingFolderId: Long? = null,
    enterFolder: (Long, String) -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(folders, key = { folder -> "folder-${folder.id}" }) { folder ->
            Card(
                onClick = { enterFolder(folder.id, folder.name) },
                enabled = movingFolderId != folder.id,
                modifier = Modifier.alpha(if (movingFolderId == folder.id) 0.5f else 1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.baseline_folder_48),
                        contentDescription = "Folder",
                        modifier = Modifier.padding(8.dp)
                            .size(24.dp)
                    )
                    Text(
                        folder.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Image(
                    painter = painterResource(R.drawable.baseline_folder_48),
                    contentDescription = "Folder",
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
