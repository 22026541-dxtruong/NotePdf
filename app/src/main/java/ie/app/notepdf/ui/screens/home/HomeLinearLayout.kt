package ie.app.notepdf.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ie.app.notepdf.R
import ie.app.notepdf.data.local.entity.Document
import ie.app.notepdf.data.local.entity.Folder
import ie.app.notepdf.data.local.relation.FolderWithSub

@Composable
fun HomeLinearLayout(
    folderWithSub: FolderWithSub? = null,
    enterFolder: (Long, String) -> Unit,
    deleteFolder: (Folder) -> Unit = {},
    editFolderName: (Folder) -> Unit = {},
    moveFolder: (Folder) -> Unit = {},
    deleteFile: (Document) -> Unit = {},
    editFileName: (Document) -> Unit = {},
    moveFile: (Document) -> Unit = {},
    openFile: (Document) -> Unit = {}
) {
    folderWithSub?.let {
        LazyColumn {
            items(it.subFolders, key = { folder -> "folder-${folder.id}" }) { folder ->
                LinearFolderItem(
                    folder,
                    enterFolder,
                    deleteFolder,
                    moveFolder,
                    editFolderName,
                    modifier = Modifier
                        .animateItem()
                )
            }
            items(it.subDocuments, key = { pdf -> "file-${pdf.id}" }) { pdf ->
                LinearFileItem(
                    pdf,
                    deleteFile,
                    editFileName,
                    moveFile,
                    openFile,
                    modifier = Modifier
                        .animateItem()
                )
            }
        }
    }
}

@Composable
fun LinearFolderItem(
    folder: Folder,
    enterFolder: (Long, String) -> Unit,
    deleteFolder: (Folder) -> Unit = {},
    moveFolder: (Folder) -> Unit = {},
    editFolderName: (Folder) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { enterFolder(folder.id, folder.name) },
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        ListItem(
            leadingContent = {
                Image(
                    painter = painterResource(R.drawable.baseline_folder_48),
                    contentDescription = "Folder"
                )

            },
            headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(folder.createdAt)
            },
            trailingContent = {
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
        )
    }
}

@Composable
fun LinearFileItem(
    pdf: Document,
    deleteFile: (Document) -> Unit = {},
    editFileName: (Document) -> Unit = {},
    moveFile: (Document) -> Unit = {},
    openFile: (Document) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { openFile(pdf) },
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        ListItem(
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .wrapContentSize()
                ) {
                    Image(
                        painter = painterResource(R.drawable.baseline_picture_as_pdf_24),
                        contentDescription = "Pdf"
                    )
                }
            },
            headlineContent = { Text(pdf.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(pdf.createdAt)
            },
            trailingContent = {
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
        )
    }
}

@Composable
fun FolderListLinear(
    folders: List<Folder>,
    movingFolderId: Long? = null,
    enterFolder: (Long, String) -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
    ) {
        items(folders) { folder ->
            Surface(
                onClick = { enterFolder(folder.id, folder.name) },
                shape = RoundedCornerShape(8.dp),
                enabled = movingFolderId != folder.id,
                modifier = Modifier.alpha(if (movingFolderId == folder.id) 0.5f else 1f)
            ) {
                ListItem(
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.baseline_folder_48),
                            contentDescription = "Folder"
                        )

                    },
                    headlineContent = { Text(folder.name) },
                    supportingContent = {
                        Text(folder.createdAt)
                    },
                )
            }
        }
    }
}
