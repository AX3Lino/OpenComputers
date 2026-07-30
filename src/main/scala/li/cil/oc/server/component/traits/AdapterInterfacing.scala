package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Node
import li.cil.oc.api.prefab
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.common.util.ForgeDirection

import scala.collection.mutable

// Adapter's neighbor-block driver scanning (tileentity.traits.AdapterInterfacing), adapted for a
// microcontroller-hosted card: anchored to the host's world position, the same way Transposer.Upgrade
// already reaches adjacent inventories for item/fluid transfer, instead of to a tile entity of its
// own (a card has none). There's no world neighbor-change event to hook here, so rescanning is
// periodic instead of purely event-driven; an initial scan still runs immediately on network connect.
trait AdapterInterfacing extends prefab.ManagedEnvironment with WorldAware {
  private val blocks = Array.fill[Option[(ManagedEnvironment, api.driver.SidedBlock)]](6)(None)

  private val updatingBlocks = mutable.ArrayBuffer.empty[ManagedEnvironment]

  private val blocksData = Array.fill[Option[BlockData]](6)(None)

  private var scanTimer = 0

  // ----------------------------------------------------------------------- //

  /** Name and network address of whatever driver-backed component ended up connected on the given side, if any. */
  def connectedComponent(side: ForgeDirection): Option[(String, String)] = blocks(side.ordinal()).flatMap { case (environment, _) =>
    environment.node match {
      case component: Component => Some(component.name -> component.address)
      case _ => None
    }
  }

  // ----------------------------------------------------------------------- //

  override def canUpdate = true

  override def update() {
    if (updatingBlocks.nonEmpty) {
      for (block <- updatingBlocks) {
        block.update()
      }
    }
    scanTimer += 1
    if (scanTimer >= Settings.get.tickFrequency) {
      scanTimer = 0
      neighborChanged()
    }
  }

  def neighborChanged(d: ForgeDirection) {
    if (node != null && node.network != null) {
      val npos = position.offset(d)
      world.getTileEntity(npos.x, npos.y, npos.z) match {
        case _: li.cil.oc.common.tileentity.traits.Environment =>
        // Don't provide adaption for our stuffs, same reasoning as the block form.
        case _ =>
          Option(api.Driver.driverFor(world, npos.x, npos.y, npos.z, d)) match {
            case Some(newDriver) => blocks(d.ordinal()) match {
              case Some((oldEnvironment, driver)) =>
                if (newDriver != driver) {
                  // Something else is there now. Clean up, then rebuild below.
                  blocks(d.ordinal()) = None
                  updatingBlocks -= oldEnvironment
                  blocksData(d.ordinal()) = None
                  node.disconnect(oldEnvironment.node)

                  val environment = newDriver.createEnvironment(world, npos.x, npos.y, npos.z, d)
                  if (environment != null) {
                    blocks(d.ordinal()) = Some((environment, newDriver))
                    if (environment.canUpdate) {
                      updatingBlocks += environment
                    }
                    blocksData(d.ordinal()) = Some(new BlockData(environment.getClass.getName, new NBTTagCompound()))
                    node.connect(environment.node)
                  }
                } // else: the more things change, the more they stay the same.
              case _ =>
                // A challenger appears. Maybe.
                val environment = newDriver.createEnvironment(world, npos.x, npos.y, npos.z, d)
                if (environment != null) {
                  blocks(d.ordinal()) = Some((environment, newDriver))
                  if (environment.canUpdate) {
                    updatingBlocks += environment
                  }
                  blocksData(d.ordinal()) match {
                    case Some(data) if data.name == environment.getClass.getName =>
                      environment.load(data.data)
                    case _ =>
                  }
                  blocksData(d.ordinal()) = Some(new BlockData(environment.getClass.getName, new NBTTagCompound()))
                  node.connect(environment.node)
                }
            }
            case _ => blocks(d.ordinal()) match {
              case Some((environment, _)) =>
                // We had something there, but it's gone now...
                node.disconnect(environment.node)
                environment.save(blocksData(d.ordinal()).get.data)
                Option(environment.node).foreach(_.remove())
                blocks(d.ordinal()) = None
                updatingBlocks -= environment
              case _ => // Nothing before, nothing now.
            }
          }
      }
    }
  }

  def neighborChanged() {
    if (node != null && node.network != null) {
      for (d <- ForgeDirection.VALID_DIRECTIONS) {
        neighborChanged(d)
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def onConnect(node: Node) {
    super.onConnect(node)
    if (node == this.node) {
      neighborChanged()
    }
  }

  override def onDisconnect(node: Node) {
    super.onDisconnect(node)
    if (node == this.node) {
      for (i <- blocks.indices) {
        blocks(i) match {
          case Some((environment, _)) => Option(environment.node).foreach(_.remove())
          case _ =>
        }
        blocks(i) = None
      }
      updatingBlocks.clear()
    }
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: NBTTagCompound) {
    super.load(nbt)
    val blocksNbt = nbt.getTagList(Settings.namespace + "adapter.blocks", NBT.TAG_COMPOUND)
    (0 until (blocksNbt.tagCount min blocksData.length)).
      map(blocksNbt.getCompoundTagAt).
      zipWithIndex.
      foreach {
      case (blockNbt, i) =>
        if (blockNbt.hasKey("name") && blockNbt.hasKey("data")) {
          blocksData(i) = Some(new BlockData(blockNbt.getString("name"), blockNbt.getCompoundTag("data")))
        }
    }
  }

  override def save(nbt: NBTTagCompound) {
    super.save(nbt)
    val blocksNbt = new NBTTagList()
    for (i <- blocks.indices) {
      val blockNbt = new NBTTagCompound()
      blocksData(i) match {
        case Some(data) =>
          blocks(i) match {
            case Some((environment, _)) => environment.save(data.data)
            case _ =>
          }
          blockNbt.setString("name", data.name)
          blockNbt.setTag("data", data.data)
        case _ =>
      }
      blocksNbt.appendTag(blockNbt)
    }
    nbt.setTag(Settings.namespace + "adapter.blocks", blocksNbt)
  }

  // ----------------------------------------------------------------------- //

  private class BlockData(val name: String, val data: NBTTagCompound)
}
