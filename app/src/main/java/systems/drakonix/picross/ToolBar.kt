package systems.drakonix.picross

import android.content.res.ColorStateList
import android.widget.ToggleButton

/**
 * The Fill / Mark / Pan toggle row. The selected tool's button is filled
 * with the theme accent. When [interactive] is false the buttons only
 * display the tool (the tutorial selects it).
 */
class ToolBar(
    private val board: PicrossView,
    private val toggles: Map<PicrossView.Tool, ToggleButton>,
    private val palette: Palette,
    interactive: Boolean = true,
) {
    private val plainTint = palette.bg.blendToward(palette.fg, 0.12f)

    init {
        toggles.forEach { (tool, button) ->
            if (interactive) button.setOnClickListener { select(tool) } else button.isClickable = false
        }
        select(board.tool)
    }

    fun select(tool: PicrossView.Tool) {
        board.tool = tool
        toggles.forEach { (t, button) ->
            val selected = t == tool
            button.isChecked = selected
            button.backgroundTintList = ColorStateList.valueOf(if (selected) palette.accent else plainTint)
            button.setTextColor(if (selected) palette.bg else palette.fg)
        }
    }
}
