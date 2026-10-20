# workflow-service v2 end-to-end verification (ASCII only to avoid encoding issues)
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8090'

function Call($method, $path, $body) {
    $params = @{ Method = $method; Uri = "$base$path"; ContentType = 'application/json' }
    if ($null -ne $body) { $params.Body = ($body | ConvertTo-Json -Depth 10) }
    return Invoke-RestMethod @params
}

Write-Host '=== 1. field qty ==='
$fields = Call GET '/api/field' $null
if ($fields.data -and ($fields.data | Where-Object { $_.fieldKey -eq 'qty' })) {
    Write-Host 'qty exists'
} else {
    $r = Call POST '/api/field' @{ fieldKey = 'qty'; fieldName = 'Quantity'; fieldType = 'number'; required = $true }
    Write-Host "created qty: code=$($r.code)"
}

Write-Host '=== 2. create process ==='
$def = Call POST '/api/process' @{ code = 'PR-E2E2'; name = 'E2E verify flow'; description = 'exclusive routing + reject strategy' }
$defId = $def.data.id
Write-Host "defId=$defId"

Write-Host '=== 3. save graph ==='
$graph = @{
    nodes = @(
        @{ nodeKey = 'start'; nodeName = 'Start'; nodeType = 'start' },
        @{ nodeKey = 'fill_1'; nodeName = 'Purchase form'; nodeType = 'fill' },
        @{ nodeKey = 'ex1'; nodeName = 'Exclusive split'; nodeType = 'exclusiveSplit' },
        @{ nodeKey = 'dept_approve'; nodeName = 'Dept approval'; nodeType = 'approval'; rejectStrategy = 'back_prev' },
        @{ nodeKey = 'fin_approve'; nodeName = 'Finance approval'; nodeType = 'approval'; rejectStrategy = 'terminate' },
        @{ nodeKey = 'exj1'; nodeName = 'Exclusive join'; nodeType = 'exclusiveJoin' },
        @{ nodeKey = 'end'; nodeName = 'End'; nodeType = 'end' }
    )
    links = @(
        @{ fromKey = 'start'; toKey = 'fill_1'; sortNo = 0 },
        @{ fromKey = 'fill_1'; toKey = 'ex1'; sortNo = 0 },
        @{ fromKey = 'ex1'; toKey = 'dept_approve'; sortNo = 0; condition = @{ fieldKey = 'qty'; fieldName = 'Quantity'; operator = 'gt'; value = '100' } },
        @{ fromKey = 'ex1'; toKey = 'fin_approve'; sortNo = 1; condition = @{ isDefault = $true } },
        @{ fromKey = 'dept_approve'; toKey = 'exj1'; sortNo = 0 },
        @{ fromKey = 'fin_approve'; toKey = 'exj1'; sortNo = 0 },
        @{ fromKey = 'exj1'; toKey = 'end'; sortNo = 0 }
    )
}
$saved = Call PUT "/api/process/$defId/graph" $graph
$fillNode = $saved.data.nodes | Where-Object { $_.nodeKey -eq 'fill_1' }
Write-Host "graph saved, fill_1 node id=$($fillNode.id)"

Write-Host '=== 4. attach qty field to fill_1 ==='
$nf = Call POST "/api/process/nodes/$($fillNode.id)/fields" @{ fieldKey = 'qty'; orderNo = 1; mode = 'editable' }
Write-Host "field attach: code=$($nf.code)"

Write-Host '=== 5. publish (BPMN compile + deploy) ==='
$pub = Call POST "/api/process/$defId/publish" $null
Write-Host "publish: version=$($pub.data.version) deploymentId=$($pub.data.deploymentId)"

Write-Host '=== 6. start instance A (qty=50 -> default branch: finance) ==='
$instA = Call POST '/api/instance/start' @{
    processDefId = $defId; applicant = 'zhangsan'; bizNo = 'PR-A-001'; title = 'Test A small'
    formData = @{ qty = 50 }
    assignees = @{ dept_approve = 'manager_li'; fin_approve = 'finance_wang' }
}
$instAId = $instA.data.id
Write-Host "instanceA id=$instAId flowableId=$($instA.data.flowableInstanceId)"

Write-Host '=== 7. zhangsan submits fill task ==='
$tasksZh = Call GET '/api/task/my?assignee=zhangsan' $null
$fillTask = $tasksZh.data | Where-Object { $_.nodeType -eq 'fill' } | Select-Object -First 1
Write-Host "zhangsan task: taskId=$($fillTask.taskId) nodeKey=$($fillTask.nodeKey)"
if ($fillTask) {
    $sub = Call POST '/api/task/submit' @{ taskId = $fillTask.taskId; formData = @{ qty = 50 } }
    Write-Host "submit: code=$($sub.code)"
}

Write-Host '=== 8. finance_wang tasks (default branch) ==='
$tasksA = Call GET '/api/task/my?assignee=finance_wang' $null
Write-Host "finance_wang task count: $($tasksA.data.Count)"
foreach ($t in $tasksA.data) { Write-Host "  taskId=$($t.taskId) nodeKey=$($t.nodeKey) bizNo=$($t.bizNo)" }

Write-Host '=== 9. finance rejects (terminate strategy) ==='
$finTask = $tasksA.data | Select-Object -First 1
if ($finTask) {
    $ap = Call POST '/api/task/approve' @{ taskId = $finTask.taskId; approved = $false; comment = 'reject: over budget' }
    Write-Host "reject: code=$($ap.code)"
} else { Write-Host 'no finance task, skip' }

Write-Host '=== 10. instance A schema (expect finished=true) ==='
$schemaA = Call GET "/api/instance/$instAId/schema" $null
Write-Host "finished=$($schemaA.data.finished) nodeType=$($schemaA.data.nodeType) endActivityId=$($schemaA.data.endActivityId)"

Write-Host '=== 11. instance A records ==='
$recs = Call GET "/api/instance/$instAId/records" $null
foreach ($r in $recs.data) { Write-Host "  [$($r.actionType)] $($r.nodeName) by $($r.executor) comment=$($r.comment)" }

Write-Host ''
Write-Host '=== 12. start instance B (qty=200 -> conditional branch: dept) ==='
$instB = Call POST '/api/instance/start' @{
    processDefId = $defId; applicant = 'zhangsan'; bizNo = 'PR-B-001'; title = 'Test B big'
    formData = @{ qty = 200 }
    assignees = @{ dept_approve = 'manager_li'; fin_approve = 'finance_wang' }
}
$instBId = $instB.data.id
Write-Host "instanceB id=$instBId"

Write-Host '=== 13. zhangsan submits instance B fill ==='
$tasksZhB = Call GET '/api/task/my?assignee=zhangsan' $null
$fillTaskB = $tasksZhB.data | Where-Object { $_.nodeType -eq 'fill' -and $_.bizNo -eq 'PR-B-001' } | Select-Object -First 1
if ($fillTaskB) {
    $subB = Call POST '/api/task/submit' @{ taskId = $fillTaskB.taskId; formData = @{ qty = 200 } }
    Write-Host "submit: code=$($subB.code)"
} else { Write-Host 'instance B fill task not found' }

$tasksB = Call GET '/api/task/my?assignee=manager_li' $null
Write-Host "manager_li task count: $($tasksB.data.Count) (expect PR-B-001 dept_approve)"
foreach ($t in $tasksB.data) { Write-Host "  taskId=$($t.taskId) nodeKey=$($t.nodeKey) bizNo=$($t.bizNo)" }

Write-Host '=== 14. manager approves -> join -> end ==='
$deptTask = $tasksB.data | Where-Object { $_.bizNo -eq 'PR-B-001' } | Select-Object -First 1
if ($deptTask) {
    $ap2 = Call POST '/api/task/approve' @{ taskId = $deptTask.taskId; approved = $true; comment = 'approved' }
    $schemaB = Call GET "/api/instance/$instBId/schema" $null
    Write-Host "after approve: finished=$($schemaB.data.finished)"
    $recsB = Call GET "/api/instance/$instBId/records" $null
    foreach ($r in $recsB.data) { Write-Host "  [$($r.actionType)] $($r.nodeName) by $($r.executor) comment=$($r.comment)" }
} else { Write-Host 'dept task not found' }

Write-Host ''
Write-Host '=== verification done ==='
