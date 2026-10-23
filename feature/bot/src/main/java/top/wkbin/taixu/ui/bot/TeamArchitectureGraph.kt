package top.wkbin.taixu.ui.bot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import top.wkbin.taixu.core.model.bot.*
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeTextButton

/** Native Compose graph: scroll horizontally to inspect nodes, click a node to edit its manager. */
@Composable
internal fun TeamArchitectureGraph(team: GroupTeam, governance: GroupGovernance, members: List<Bot>,
    editable: Boolean = false, onSelectMember: (String) -> Unit = {}) {
    val graph = remember(team, governance) { layoutTeamGraph(team, governance) }
    val managementColor = MaterialTheme.colorScheme.primary
    val reviewColor = MaterialTheme.colorScheme.tertiary
    val rejectionColor = MaterialTheme.colorScheme.error
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val names = members.associateBy { it.id }
    var fit by rememberSaveable { mutableStateOf(true) }
    var manualScale by rememberSaveable { mutableFloatStateOf(1f) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("实线：直属管理　虚线：审议／准奏　红色虚线：封驳", style = MaterialTheme.typography.bodySmall)
        Text(if (editable) "全图查看关系，放大后点击成员调整上级；可横向滑动。" else "全图查看关系，放大后可横向滑动查看详情。", style = MaterialTheme.typography.bodySmall)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scale = if (fit) minOf(1f, maxWidth.value / graph.width) else manualScale
            Column {
            Row {
                RuntimeTextButton(onClick = { fit = true }) { Text("全图") }
                RuntimeTextButton(onClick = { fit = false; manualScale = 1f }) { Text("原尺寸") }
                RuntimeTextButton(onClick = { manualScale = (scale / 1.4f).coerceAtLeast(.1f); fit = false }) { Text("−") }
                RuntimeTextButton(onClick = { manualScale = (scale * 1.4f).coerceAtMost(2f); fit = false }) { Text("＋") }
            }
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Box(Modifier.size((graph.width * scale).dp, (graph.height * scale).dp)) {
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(graph.width.dp, graph.height.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }) {
                Canvas(Modifier.fillMaxSize()) {
                    val nodes = graph.nodes.associateBy { it.id }
                    graph.edges.forEach { edge ->
                        val from = nodes[edge.from] ?: return@forEach
                        val to = nodes[edge.to] ?: return@forEach
                        val management = edge.kind == TeamGraphEdgeKind.MANAGEMENT
                        val rejection = edge.kind == TeamGraphEdgeKind.REJECTION
                        val color = if (management) managementColor else if (rejection) rejectionColor else reviewColor
                        val start: Offset
                        val end: Offset
                        val path = Path()
                        if (management) {
                            start = Offset((from.x + 80f).dp.toPx(), (from.y + 76f).dp.toPx())
                            end = Offset((to.x + 80f).dp.toPx(), to.y.dp.toPx())
                            path.moveTo(start.x, start.y)
                            val middle = (start.y + end.y) / 2
                            path.lineTo(start.x, middle); path.lineTo(end.x, middle); path.lineTo(end.x, end.y)
                        } else {
                            val right = to.x > from.x
                            start = Offset((from.x + if (right) 160f else 0f).dp.toPx(), (from.y + if (rejection) 62f else 25f).dp.toPx())
                            end = Offset((to.x + if (right) 0f else 160f).dp.toPx(), (to.y + if (rejection) 62f else 25f).dp.toPx())
                            path.moveTo(start.x, start.y)
                            val lane = minOf(start.y, end.y) + (if (rejection) 44.dp.toPx() else -34.dp.toPx())
                            path.cubicTo(start.x + (end.x - start.x) / 3, lane, start.x + (end.x - start.x) * 2 / 3, lane, end.x, end.y)
                        }
                        drawPath(path, color, style = Stroke(2.dp.toPx(), pathEffect = if (management) null else PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 5.dp.toPx()))))
                        val angle = if (management) Math.PI.toFloat() / 2 else atan2(end.y - start.y, end.x - start.x)
                        val arrow = Path().apply {
                            moveTo(end.x, end.y)
                            lineTo(end.x - 9.dp.toPx() * cos(angle - .45f), end.y - 9.dp.toPx() * sin(angle - .45f))
                            moveTo(end.x, end.y)
                            lineTo(end.x - 9.dp.toPx() * cos(angle + .45f), end.y - 9.dp.toPx() * sin(angle + .45f))
                        }
                        drawPath(arrow, color, style = Stroke(2.dp.toPx()))
                        val label = when (edge.kind) {
                            TeamGraphEdgeKind.MANAGEMENT -> "直属"
                            TeamGraphEdgeKind.REVIEW -> "送审"
                            TeamGraphEdgeKind.APPROVED_EXECUTION -> "准奏执行"
                            TeamGraphEdgeKind.REJECTION -> "封驳"
                        }
                        val labelY = if (management) (start.y + end.y) / 2 - 16.dp.toPx()
                            else minOf(start.y, end.y) + (if (rejection) 24.dp.toPx() else -34.dp.toPx())
                        drawText(measurer, label, topLeft = Offset((start.x + end.x) / 2 - 14.dp.toPx(), labelY), style = labelStyle.copy(color = color))
                    }
                }
                graph.nodes.forEach { node ->
                    RuntimeCard(modifier = Modifier.offset(node.x.dp, node.y.dp).width(160.dp).heightIn(min = 76.dp),
                        onClick = if (editable) ({ onSelectMember(node.id) }) else null, contentPadding = PaddingValues(10.dp)) {
                        Column {
                            Text(names[node.id]?.name ?: node.id, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (node.id == team.adminBotId) "团队主控／裁决" else names[node.id]?.role.orEmpty(),
                                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            }
            }
            }
        }
        governance.rules.forEach { rule ->
            Text("${rule.name}：${names[rule.authorId]?.name} → 审议 ${names[rule.reviewerId]?.name} → 准奏执行 ${names[rule.executorId]?.name}；封驳返回起草者",
                style = MaterialTheme.typography.bodySmall, color = reviewColor)
        }
    }
}
