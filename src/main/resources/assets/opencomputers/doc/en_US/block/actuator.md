# Actuator

![Reaching out and touching something.](oredict:oc:actuator)

The actuator connects an ME network directly to a single adjacent block, the same way an [adapter](adapter.md) does - but it only ever touches whatever is on its one wrench-set facing side, and rotates the same way a hopper does.

It can move items between the ME network and the inventory on its facing side, and scan that block for its name, position and metadata - the same fields GregTech's own portable scanner reports, plus the ghost circuit configuration a scanner can't show, which can also be changed remotely without opening the machine's GUI.

*Note that, unlike the [transposer](transposer.md), the actuator has no internal inventory - transfers are direct between the ME network and the facing block.*

Like the [geolyzer](geolyzer.md), it can also be installed in a [microcontroller](microcontroller.md) as an upgrade, where it acts on the microcontroller's wrench-set output side. The microcontroller must then be cabled into an ME network, the same way the block is.
