# Office 3D Asset Implementation Spec (System-Aligned)

## 1. Purpose
This spec is the implementation baseline for the office floor digital twin in AegisAIWorkbech, aligned with the current runtime data model and visualization bindings.

## 2. System-Truth Mapping (Current)
- Role codes in system: ADMIN, SEC, SECOPS, DATA_ADMIN, AUDIT.
- Department values in system: 合规部, 安全部, 数据部, 审计部 (plus legacy aliases in historical payloads).
- Desk scale in current runtime: 24 desks.
- Canonical desk naming: Desk_001 ... Desk_024.

## 3. Required GLB Nodes
Mandatory nodes for v1 integration:
- Desk_001 ... Desk_024
- Dept_Research
- Dept_Product
- Dept_Marketing
- Dept_Finance
- Dept_Ops
- Control_Root
- Pulse_Center

## 4. Runtime Binding Contract
Each desk/department trigger should include extras metadata in GLB:

```json
{
  "type": "desk",
  "deskId": "Desk_001",
  "department": "Research",
  "roleScope": ["ADMIN", "SECOPS", "DATA_ADMIN", "AUDIT", "EMPLOYEE"]
}
```

Department trigger example:

```json
{
  "type": "department",
  "nodeId": "Dept_Finance",
  "aliases": ["财务", "审计", "合规", "finance", "audit"]
}
```

## 5. Frontend Implementation Status
Implemented in code:
- Canonical desk ID generation/normalization: src/utils/officeDigitalTwin.js
- Event -> Desk_XXX deterministic mapping: src/views/EmployeeAiBehaviorMonitor.vue
- Desk metadata pipeline (department, role, deptNode, roleTier): src/views/EmployeeAiBehaviorMonitor.vue
- Local FBX office model injection: src/views/EmployeeAiBehaviorMonitor.vue
- Real FBX anchor mapping for SM_DeskPreset_*, SM_Desk_*, SM_TablePreset_*, SM_Chair*: src/components/ComplianceOfficeSimulator.vue
- Alert camera jump and zone-aware context display: src/components/ComplianceOfficeSimulator.vue

Runtime config:
- The simulator now uses the local FBX asset `0b1e521b7174458b861916c8f12bf103.fbx` as the primary model source.
- If the FBX cannot be loaded, simulator falls back to the built-in procedural scene and shows the failure reason in the asset diagnostic panel.

## 6. Blender Export Rules (Execution)
- Unit: meters.
- Axis in Blender: Z-up.
- GLB export: -Z Forward, +Y Up.
- Embed textures into GLB.
- Triangulate on export.
- Keep Empty objects used for trigger nodes.

## 7. Validation Checklist
Before handing off the FBX to frontend:
1. Verify the model contains stable anchor names for desk-like nodes, especially `SM_DeskPreset_*`, `SM_Desk_*`, `SM_TablePreset_*`, and `SM_Chair*`.
2. Verify desk anchors are individually raycastable or at least retain stable world transforms for mapping.
3. Verify emissive channel exists for desk materials.
4. Verify Control_Root and Pulse_Center exist and are positioned correctly.
5. Verify GLB size target (compressed) stays under 60MB.

## 8. Phase-2 Data Upgrade
Current system has no native persistent floor/desk binding table. v1 uses deterministic mapping. v2 should add server-side desk_binding to replace hash mapping with explicit user/device -> desk mapping.
