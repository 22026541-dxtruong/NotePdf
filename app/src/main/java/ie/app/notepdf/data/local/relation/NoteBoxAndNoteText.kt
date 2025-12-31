package ie.app.notepdf.data.local.relation

import ie.app.notepdf.data.local.entity.NoteBox
import ie.app.notepdf.data.local.entity.NoteText

data class NoteBoxAndNoteText(
    val noteBoxs: List<NoteBox> = emptyList(),
    val noteTexts: List<NoteText> = emptyList()
)
