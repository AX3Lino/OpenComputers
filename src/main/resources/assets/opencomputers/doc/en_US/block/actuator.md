# Actuator

![Reaching out and touching something.](oredict:oc:actuator)

The actuator connects an ME network directly to a single adjacent block, the same way an [adapter](adapter.md) does - but it only ever touches whatever is on its one wrench-set facing side, and rotates the same way a hopper does.

It can move items between the ME network and the inventory on its facing side, scan that block for its name, and, if it's a GregTech machine, its activity, progress and ghost circuit configuration - including changing that circuit remotely, without opening the machine's GUI.

*Note that, unlike the [transposer](transposer.md), the actuator has no internal inventory - transfers are direct between the ME network and the facing block.*
