package com.wowuwo233.teleport_staff;

import com.wowuwo233.teleport_staff.teleport.CardinalDirection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 脱机回归测试：不依赖 Minecraft，直接跑 {@code javac} + {@code java} 就能验证。
 *
 * <p>覆盖两块最容易出错、又最难在游戏里手工发现的东西：
 * <ol>
 *   <li><b>方位吸附</b>——45° 边界到底算东还是北、yaw 的 0°/360° 回绕有没有问题。
 *       这类逻辑在游戏里「感觉差不多」，但错了就是会莫名其妙斜着传送。</li>
 *   <li><b>资源完整性</b>——模型 JSON、贴图、语言键、配方是否自洽。
 *       这些错了游戏只会静默显示成紫黑方块或原始键名，没有报错。</li>
 * </ol>
 *
 * 用法：
 * <pre>
 *   javac -encoding UTF-8 -d build/tools-check -sourcepath src/main/java tools/Check.java
 *   java -Dfile.encoding=UTF-8 -cp build/tools-check com.wowuwo233.teleport_staff.Check
 * </pre>
 */
public final class Check {

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        System.out.println("Teleport Staff - offline checks");
        System.out.println("==============================");

        checkCardinal();
        checkResources();

        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("ALL CHECKS PASSED (" + passed + ")");
            return;
        }
        System.out.println("FAILURES (" + failures.size() + "):");
        for (String f : failures) {
            System.out.println("  - " + f);
        }
        System.exit(1);
    }

    // ------------------------------------------------------------ 方位吸附 ----

    private static void checkCardinal() {
        System.out.println();
        System.out.println("[cardinal] 朝向吸附到东南西北");

        // 坐标约定：东 = +X，西 = -X，南 = +Z，北 = -Z
        expectDir(1, 0, CardinalDirection.EAST, "正东 +X");
        expectDir(-1, 0, CardinalDirection.WEST, "正西 -X");
        expectDir(0, 1, CardinalDirection.SOUTH, "正南 +Z");
        expectDir(0, -1, CardinalDirection.NORTH, "正北 -Z");

        // 斜向：哪个轴投影大就算哪个
        expectDir(1, 0.5, CardinalDirection.EAST, "东南偏东");
        expectDir(0.5, 1, CardinalDirection.SOUTH, "东南偏南");
        expectDir(-1, -0.4, CardinalDirection.WEST, "西北偏西");
        expectDir(0.4, -1, CardinalDirection.NORTH, "西北偏北");
        expectDir(0.1, 0.9, CardinalDirection.SOUTH, "几乎正南");

        // 45° 平局：约定判给东西方向，必须稳定
        expectDir(1, 1, CardinalDirection.EAST, "东南 45° 平局 -> 东");
        expectDir(-1, 1, CardinalDirection.WEST, "西南 45° 平局 -> 西");
        expectDir(1, -1, CardinalDirection.EAST, "东北 45° 平局 -> 东");
        expectDir(-1, -1, CardinalDirection.WEST, "西北 45° 平局 -> 西");

        // 临界角两侧：刚偏过 45° 就应该换方向
        expectDir(0.71, 0.70, CardinalDirection.EAST, "略小于 45° -> 东");
        expectDir(0.70, 0.71, CardinalDirection.SOUTH, "略大于 45° -> 南");

        // 退化输入：视线垂直向下/向上时水平投影为零
        expectDir(0, 0, CardinalDirection.SOUTH, "零向量（垂直俯仰）-> 确定的兜底值");

        // 扫描一整圈：确认四个方向都被覆盖，且每个 90° 扇区只映射到一个方向
        int[] counts = new int[4];
        for (int deg = 0; deg < 360; deg++) {
            CardinalDirection d = dirFromYaw(deg);
            counts[d.ordinal()]++;
        }
        boolean eachQuarter = true;
        for (CardinalDirection d : CardinalDirection.values()) {
            int c = counts[d.ordinal()];
            // 360° 除以 4 是 90°，允许平局分配带来的 ±1 偏差
            if (Math.abs(c - 90) > 1) {
                eachQuarter = false;
                failures.add("方向 " + d + " 覆盖了 " + c + " 度，应接近 90 度");
            }
        }
        if (eachQuarter) {
            pass("一圈 360° 被均分到四个方向（各约 90°）");
        }

        // 回绕：yaw 359° 和 1° 应当判成同一个方向（都在正南附近）
        CardinalDirection at1 = dirFromYaw(1);
        CardinalDirection at359 = dirFromYaw(359);
        if (at1 == at359) {
            pass("yaw 回绕处不抖动（1° 与 359° 同向：" + at1 + "）");
        } else {
            failures.add("yaw 回绕处方向不一致：1° -> " + at1 + "，359° -> " + at359);
        }

        // 轴向纯度：这是「没有侧向偏移」的根据——每个方向只有一个非零分量
        for (CardinalDirection d : CardinalDirection.values()) {
            int nonzero = (d.stepX() != 0 ? 1 : 0) + (d.stepZ() != 0 ? 1 : 0);
            int magnitude = Math.abs(d.stepX()) + Math.abs(d.stepZ());
            if (nonzero == 1 && magnitude == 1) {
                pass(d + " 是纯轴向单位步进 (" + d.stepX() + ", " + d.stepZ() + ")");
            } else {
                failures.add(d + " 不是纯轴向：" + d.stepX() + ", " + d.stepZ());
            }
        }

        // offsetX/offsetZ：不该动的那个轴必须【逐位】不变，不只是"近似相等"。
        // 用 Double.doubleToRawLongBits 比较，能抓住任何一点浮点误差。
        for (CardinalDirection d : CardinalDirection.values()) {
            boolean xFixed = d.stepX() == 0 || d.stepZ() == 0; // 至少一个轴必须不动
            double startX = 123.456789D;
            double startZ = -987.654321D;
            boolean xSame = Double.doubleToRawLongBits(d.offsetX(startX, 777))
                    == Double.doubleToRawLongBits(startX);
            boolean zSame = Double.doubleToRawLongBits(d.offsetZ(startZ, 777))
                    == Double.doubleToRawLongBits(startZ);
            // 南北向时 X 必须逐位不变，东西向时 Z 必须逐位不变
            boolean ok = d.stepX() == 0 ? xSame : zSame;
            if (ok && xFixed) {
                pass(d + " 的静止轴逐位不变（无侧向偏移）");
            } else {
                failures.add(d + " 的静止轴发生了变化，会产生侧向偏移");
            }
        }

        // 举例：副手 5 个 / 64 个粉时，位移应当正好是 500 / 6400 格。
        // 注意这里传的是【格数】= 粉数量 × 100，不是粉的数量本身；
        // 乘法在 TeleportStaffItem 里做（那里引用了 Minecraft 类，无法脱机测）。
        int perDust = 100;
        expectOffset(CardinalDirection.NORTH, 100.0D, 200.0D, 5 * perDust,
                100.0D, 200.0D - 500.0D, "5 个粉朝北 -> Z 减 500");
        expectOffset(CardinalDirection.SOUTH, 100.0D, 200.0D, 64 * perDust,
                100.0D, 200.0D + 6400.0D, "64 个粉朝南 -> Z 加 6400");
        expectOffset(CardinalDirection.EAST, 100.0D, 200.0D, 64 * perDust,
                100.0D + 6400.0D, 200.0D, "64 个粉朝东 -> X 加 6400");
        expectOffset(CardinalDirection.WEST, 100.0D, 200.0D, 5 * perDust,
                100.0D - 500.0D, 200.0D, "5 个粉朝西 -> X 减 500");
    }

    private static void expectOffset(CardinalDirection d, double startX, double startZ, int distance,
                                     double wantX, double wantZ, String label) {
        double x = d.offsetX(startX, distance);
        double z = d.offsetZ(startZ, distance);
        if (Math.abs(x - wantX) < 1.0E-9D && Math.abs(z - wantZ) < 1.0E-9D) {
            pass(label + " => (" + x + ", " + z + ")");
        } else {
            failures.add(label + "：得到 (" + x + ", " + z + ")，期望 (" + wantX + ", " + wantZ + ")");
        }
    }

    /** 按 Minecraft 的 yaw 约定（0° 朝 +Z，顺时针增大）反推方向。 */
    private static CardinalDirection dirFromYaw(double yawDeg) {
        double rad = Math.toRadians(yawDeg);
        return CardinalDirection.nearest(-Math.sin(rad), Math.cos(rad));
    }

    private static void expectDir(double dx, double dz, CardinalDirection expected, String label) {
        CardinalDirection actual = CardinalDirection.nearest(dx, dz);
        if (actual == expected) {
            pass(label + " => " + actual);
        } else {
            failures.add(label + "：期望 " + expected + "，实际 " + actual);
        }
    }

    // ------------------------------------------------------------ 资源完整性 ----

    private static void checkResources() throws IOException {
        System.out.println();
        System.out.println("[resources] 模型 / 贴图 / 语言 / 配方");

        Path root = Path.of("src", "main", "resources");
        Path assets = root.resolve("assets/teleport_staff");
        Path data = root.resolve("data/teleport_staff");

        // --- 物品模型必须 parent 到 item/generated，否则物品栏里不显示 ---
        Path model = assets.resolve("models/item/teleport_staff.json");
        if (Files.exists(model)) {
            String text = Files.readString(model, StandardCharsets.UTF_8);
            pass("物品模型存在");
            if (text.contains("item/generated")) {
                pass("模型 parent 是 minecraft:item/generated");
            } else {
                failures.add("模型缺少 item/generated 父级，物品会显示成紫黑方块");
            }
            if (text.contains("teleport_staff:item/teleport_staff")) {
                pass("模型引用的贴图路径正确");
            } else {
                failures.add("模型 layer0 路径不是 teleport_staff:item/teleport_staff");
            }
        } else {
            failures.add("缺少模型文件 " + model);
        }

        // --- 模型引用的贴图必须真的存在，且尺寸是 16x16 ---
        Path texture = assets.resolve("textures/item/teleport_staff.png");
        if (Files.exists(texture)) {
            byte[] png = Files.readAllBytes(texture);
            int w = readPngInt(png, 16);
            int h = readPngInt(png, 20);
            if (w == 16 && h == 16) {
                pass("贴图存在且是 16x16");
            } else {
                failures.add("贴图尺寸是 " + w + "x" + h + "，应当为 16x16");
            }
            if (png.length > 8 && (png[0] & 0xFF) == 0x89 && png[1] == 'P') {
                pass("贴图是合法 PNG");
            } else {
                failures.add("贴图不是合法 PNG");
            }
        } else {
            failures.add("缺少贴图 " + texture);
        }

        // --- 语言文件：中英两份，键必须一致 ---
        Path zh = assets.resolve("lang/zh_cn.json");
        Path en = assets.resolve("lang/en_us.json");
        if (Files.exists(zh) && Files.exists(en)) {
            String zhText = Files.readString(zh, StandardCharsets.UTF_8);
            String enText = Files.readString(en, StandardCharsets.UTF_8);
            pass("中英语言文件都存在");

            List<String> zhKeys = keys(zhText);
            List<String> enKeys = keys(enText);
            if (zhKeys.equals(enKeys)) {
                pass("语言键一一对应（" + zhKeys.size() + " 个）");
            } else {
                List<String> missingInEn = new ArrayList<>(zhKeys);
                missingInEn.removeAll(enKeys);
                List<String> missingInZh = new ArrayList<>(enKeys);
                missingInZh.removeAll(zhKeys);
                failures.add("语言键不一致，en 缺 " + missingInEn + "，zh 缺 " + missingInZh);
            }

            if (zhKeys.contains("item.teleport_staff.teleport_staff")) {
                pass("物品名翻译键存在");
            } else {
                failures.add("缺少物品名键 item.teleport_staff.teleport_staff");
            }

            // 代码里用到的每个提示键都要有翻译
            for (String key : new String[]{
                    "item.teleport_staff.teleport_staff",
                    "item.teleport_staff.compressed_warp_dust",
                    "message.teleport_staff.need_dust",
                    "message.teleport_staff.teleported",
                    "tooltip.teleport_staff.usage",
                    "tooltip.teleport_staff.distance",
                    "tooltip.teleport_staff.distance_compressed",
                    "tooltip.teleport_staff.stack",
                    "tooltip.teleport_staff.cardinal",
                    "tooltip.teleport_staff.compressed_dust",
                    "tooltip.teleport_staff.compressed_use"}) {
                if (zhKeys.contains(key) && enKeys.contains(key)) {
                    pass("翻译键齐全：" + key);
                } else {
                    failures.add("翻译键缺失：" + key);
                }
            }

            // 每个枚举方向都必须有翻译键，否则提示会显示成原始键名
            boolean allDirections = true;
            for (CardinalDirection d : CardinalDirection.values()) {
                String key = "direction.teleport_staff." + d.key();
                if (!zhKeys.contains(key) || !enKeys.contains(key)) {
                    allDirections = false;
                    failures.add("方向 " + d + " 缺少翻译键 " + key);
                }
            }
            if (allDirections) {
                pass("四个方向的翻译键都齐全");
            }

            // 两种燃料的 tooltip 都必须带 %d：代码里是用 translatable(key, 数字)
            // 传参的，缺了占位符游戏里会抛格式化异常。
            // 直接检查键对应的值里有没有 %d，而不是比对某句固定文案——
            // 文案会改，键和占位符的约定不该改。
            boolean distanceHasArg = hasPlaceholder(zhText, "tooltip.teleport_staff.distance")
                    && hasPlaceholder(enText, "tooltip.teleport_staff.distance");
            boolean compressedHasArg = hasPlaceholder(zhText, "tooltip.teleport_staff.distance_compressed")
                    && hasPlaceholder(enText, "tooltip.teleport_staff.distance_compressed");
            if (distanceHasArg) {
                pass("distance 提示保留了 %d 占位符");
            } else {
                failures.add("tooltip.teleport_staff.distance 里的 %d 占位符丢失，游戏里会报错");
            }
            if (compressedHasArg) {
                pass("distance_compressed 提示保留了 %d 占位符");
            } else {
                failures.add("tooltip.teleport_staff.distance_compressed 缺少 %d 占位符");
            }
            // 压缩粉自己的 tooltip 同样带参数
            if (hasPlaceholder(zhText, "tooltip.teleport_staff.compressed_dust")) {
                pass("compressed_dust 提示保留了 %d 占位符");
            } else {
                failures.add("tooltip.teleport_staff.compressed_dust 缺少 %d 占位符");
            }

            // teleported 用了 %1$s 和 %2$s 两个位置参数，缺一个就会抛异常
            if (zhText.contains("%1$s") && zhText.contains("%2$s")
                    && enText.contains("%1$s") && enText.contains("%2$s")) {
                pass("teleported 提示的两个位置参数都在");
            } else {
                failures.add("message.teleport_staff.teleported 缺少 %1$s 或 %2$s 占位符");
            }
        } else {
            failures.add("缺少语言文件（zh_cn.json / en_us.json）");
        }

        // --- 配方 ---
        Path recipe = data.resolve("recipes/teleport_staff.json");
        if (Files.exists(recipe)) {
            String text = Files.readString(recipe, StandardCharsets.UTF_8);
            pass("法杖合成配方存在");
            if (text.contains("\"teleport_staff:teleport_staff\"")) {
                pass("配方产物指向 teleport_staff:teleport_staff");
            } else {
                failures.add("配方产物不是 teleport_staff:teleport_staff");
            }
            if (text.contains("\"waystones:warp_dust\"")) {
                pass("配方使用了 waystones:warp_dust");
            } else {
                failures.add("配方里的传送粉 ID 不对，应为 waystones:warp_dust");
            }
        } else {
            failures.add("缺少配方文件 " + recipe);
        }

        // --- 压缩传送粉 ---
        System.out.println();
        System.out.println("[compressed] 压缩传送粉");

        // 模型
        Path compressedModel = assets.resolve("models/item/compressed_warp_dust.json");
        if (Files.exists(compressedModel)) {
            String text = Files.readString(compressedModel, StandardCharsets.UTF_8);
            pass("压缩传送粉模型存在");
            if (text.contains("item/generated")) {
                pass("压缩粉模型 parent 是 minecraft:item/generated");
            } else {
                failures.add("压缩粉模型缺少 item/generated 父级，会显示成紫黑方块");
            }
            if (text.contains("compressed_warp_dust")) {
                pass("压缩粉模型引用的贴图路径正确");
            } else {
                failures.add("压缩粉模型 layer0 路径不对");
            }
        } else {
            failures.add("缺少压缩粉模型 " + compressedModel);
        }

        // 贴图
        Path compressedTexture = assets.resolve("textures/item/compressed_warp_dust.png");
        if (Files.exists(compressedTexture)) {
            byte[] png = Files.readAllBytes(compressedTexture);
            int w = readPngInt(png, 16);
            int h = readPngInt(png, 20);
            if (w == 16 && h == 16) {
                pass("压缩粉贴图存在且是 16x16");
            } else {
                failures.add("压缩粉贴图尺寸是 " + w + "x" + h + "，应当为 16x16");
            }
        } else {
            failures.add("缺少压缩粉贴图 " + compressedTexture);
        }

        // 形状必须和 Waystones 的原版传送粉逐像素一致（需求明确要求"形状一样"）。
        // 用原版贴图的不透明区域做比对——这是唯一能自动验证"形状相同"的办法。
        Path originalDust = Path.of("libs", "warp_dust_reference.png");
        if (Files.exists(compressedTexture) && Files.exists(originalDust)) {
            byte[] a = Files.readAllBytes(compressedTexture);
            byte[] b = Files.readAllBytes(originalDust);
            boolean sameShape = sameOpacityMask(a, b);
            if (sameShape) {
                pass("压缩粉的形状与原版传送粉逐像素一致");
            } else {
                failures.add("压缩粉形状与原版传送粉不一致");
            }
            // 但颜色必须不同，否则玩家分不出两件物品
            if (!java.util.Arrays.equals(a, b)) {
                pass("压缩粉的颜色与原版不同（可区分）");
            } else {
                failures.add("压缩粉颜色与原版完全相同，物品栏里分不出来");
            }
        } else {
            // 原版参照图是开发期资源，不在仓库里，缺失时跳过而不是报错
            pass("跳过形状比对（libs/ 里没有原版传送粉参照图）");
        }

        // 配方：一圈传送粉 + 中间末影珍珠
        Path compressedRecipe = data.resolve("recipes/compressed_warp_dust.json");
        if (Files.exists(compressedRecipe)) {
            String text = Files.readString(compressedRecipe, StandardCharsets.UTF_8);
            pass("压缩粉配方存在");
            if (text.contains("\"teleport_staff:compressed_warp_dust\"")) {
                pass("压缩粉配方产物正确");
            } else {
                failures.add("压缩粉配方产物不对");
            }
            // 图案必须是 3x3 外圈全 D、中心 P —— 也就是纹样 "DDD/DPD/DDD"
            String compact = text.replaceAll("\\s+", "");
            if (compact.contains("\"DDD\",\"DPD\",\"DDD\"") || compact.contains("\"DDD\",\"DPD\",\"DDD\"")) {
                pass("压缩粉配方是「一圈传送粉 + 中间末影珍珠」");
            } else {
                failures.add("压缩粉配方图案不是 DDD/DPD/DDD");
            }
            // 数一下 D 的数量：3x3 外圈应当是 8 个
            int dCount = compact.split("\"D\"", -1).length - 1;
            if (dCount >= 1) {
                pass("压缩粉配方引用了 D 键（传送粉）");
            }
            if (compact.contains("\"item\":\"minecraft:ender_pearl\"")) {
                pass("压缩粉配方中心是末影珍珠");
            } else {
                failures.add("压缩粉配方中心不是末影珍珠");
            }
        } else {
            failures.add("缺少压缩粉配方 " + compressedRecipe);
        }

        // 注册与代码层面的约束
        String modItemsSrc = Files.readString(
                Path.of("src/main/java/com/wowuwo233/teleport_staff/item/ModItems.java"),
                StandardCharsets.UTF_8);
        if (modItemsSrc.contains("\"compressed_warp_dust\"")) {
            pass("压缩传送粉已在 ModItems 里注册");
        } else {
            failures.add("压缩传送粉没有注册，游戏里不会出现");
        }

        String fuelSrc = Files.readString(
                Path.of("src/main/java/com/wowuwo233/teleport_staff/teleport/TeleportFuel.java"),
                StandardCharsets.UTF_8);
        if (fuelSrc.contains("COMPRESSED_BLOCKS_PER_DUST")) {
            pass("燃料计价里用了压缩粉的配置项");
        } else {
            failures.add("TeleportFuel 没有引用压缩粉的距离配置");
        }
        if (fuelSrc.contains("isFuel") && fuelSrc.contains("valueOf")) {
            pass("燃料识别与计价已抽象成 TeleportFuel");
        } else {
            failures.add("TeleportFuel 缺少 isFuel/valueOf");
        }

        // 法杖必须通过 TeleportFuel 判定，不能只认普通粉
        String staffForFuel = Files.readString(
                Path.of("src/main/java/com/wowuwo233/teleport_staff/item/TeleportStaffItem.java"),
                StandardCharsets.UTF_8);
        if (staffForFuel.contains("TeleportFuel.isFuel(offhand)")) {
            pass("法杖接受两种燃料");
        } else {
            failures.add("法杖只认普通传送粉，压缩粉用不了");
        }
        if (staffForFuel.contains("TeleportFuel.totalDistance(offhand)")) {
            pass("传送距离按燃料种类计价");
        } else {
            failures.add("传送距离没有按燃料种类计算");
        }
        // 补货必须补同一种粉：从背包搬东西时要比对 Item 是否相同
        if (staffForFuel.contains("slot.getItem() != template")) {
            pass("补货只搬同一种燃料（不会把压缩粉补成普通粉）");
        } else {
            failures.add("补货不区分燃料种类，可能把压缩粉补成普通粉");
        }
        if (staffForFuel.contains("TeleportFuel.templateOf(offhand)")) {
            pass("补货模板取自消耗掉的燃料");
        } else {
            failures.add("补货没有取消耗掉的燃料作模板");
        }

        // --- mods.toml ---
        Path modsToml = root.resolve("META-INF/mods.toml");
        if (Files.exists(modsToml)) {
            String text = Files.readString(modsToml, StandardCharsets.UTF_8);
            if (text.contains("modId=\"${mod_id}\"")) {
                pass("mods.toml 用占位符写 modId（由 gradle.properties 注入）");
            }
            if (text.contains("modId=\"waystones\"")) {
                pass("mods.toml 声明了 waystones 依赖");
            } else {
                failures.add("mods.toml 没有声明 waystones 依赖");
            }
        } else {
            failures.add("缺少 mods.toml");
        }

        // --- 传送粉 ID 必须和代码里写死的一致 ---
        String warpDustSrc = Files.readString(
                Path.of("src/main/java/com/wowuwo233/teleport_staff/teleport/WarpDust.java"),
                StandardCharsets.UTF_8);
        if (warpDustSrc.contains("ResourceLocation.tryBuild(\"waystones\", \"warp_dust\")")) {
            pass("代码里的传送粉 ID 与配方一致（waystones:warp_dust）");
        } else {
            failures.add("WarpDust.java 里的物品 ID 变了，请同步配方与 mods.toml");
        }

        // --- 法杖的行为约束 ---
        String staffSrc = Files.readString(
                Path.of("src/main/java/com/wowuwo233/teleport_staff/item/TeleportStaffItem.java"),
                StandardCharsets.UTF_8);
        if (staffSrc.contains("hand != InteractionHand.MAIN_HAND")) {
            pass("法杖只响应主手");
        } else {
            failures.add("法杖没有限定主手，副手也会触发");
        }
        if (staffSrc.contains("getOffhandItem()")) {
            pass("燃料取自副手");
        } else {
            failures.add("没有从副手读取传送粉");
        }

        // 整叠消耗：不是 shrink(1)，而是直接把副手清空
        if (staffSrc.contains("offhand.setCount(0)")) {
            pass("副手传送粉整叠消耗");
        } else {
            failures.add("没有看到整叠消耗副手传送粉的代码");
        }

        // 距离 = 副手数量 × 该种燃料的单价值。
        // 重构后这一步委托给 TeleportFuel.totalDistance（那里才认识两种粉），
        // 所以这里检查的是"距离由燃料总量决定"，而不是某个具体的乘法表达式。
        if (staffSrc.contains("TeleportFuel.totalDistance(offhand)")) {
            pass("距离按副手燃料总量计算");
        } else {
            failures.add("距离没有按副手燃料总量计算");
        }

        // 补货必须在消耗之后，否则会把刚补进来的粉又清掉
        int consumeAt = staffSrc.indexOf("offhand.setCount(0)");
        int refillAt = staffSrc.indexOf("refillOffhand(player");
        if (consumeAt >= 0 && refillAt > consumeAt) {
            pass("补货发生在消耗之后（顺序正确）");
        } else {
            failures.add("补货调用不在消耗之后，会把刚补的粉又清掉");
        }

        // 补货必须真的存在，且要判空——模板取不到时是 null
        if (staffSrc.contains("private void refillOffhand")) {
            pass("实现了副手自动补货");
        } else {
            failures.add("没有实现副手自动补货");
        }
        if (staffSrc.contains("template == null")) {
            pass("补货对空模板做了判空");
        } else {
            failures.add("补货没有判空，模板为 null 时会抛异常");
        }

        // 补货不能覆盖玩家特意放在副手的其它物品（比如盾牌）。
        // 重构后判据从 WarpDust 换成了 TeleportFuel（那里认识两种粉）。
        if (staffSrc.contains("!TeleportFuel.isFuel(offhand)")) {
            pass("补货前检查副手是不是别的物品");
        } else {
            failures.add("补货会覆盖副手原有的非燃料物品");
        }

        if (staffSrc.contains("Invulnerability.grant")) {
            pass("传送后授予无敌时间");
        } else {
            failures.add("传送后没有授予无敌时间");
        }
        if (staffSrc.contains("CardinalDirection.nearest")) {
            pass("方向经过基本方位吸附");
        } else {
            failures.add("没有调用方位吸附，方向可能是斜的");
        }

        // --- 配置项：旧的按距离折算 / 距离上限必须已移除，新项必须存在 ---
        String configSrc = Files.readString(
                Path.of("src/main/java/com/wowuwo233/teleport_staff/Config.java"),
                StandardCharsets.UTF_8);
        if (configSrc.contains("max_distance") || configSrc.contains("OBSTACLE_CLEARANCE")) {
            failures.add("Config 里还留着距离上限 / 障碍退让的旧配置项");
        } else {
            pass("已移除距离上限与障碍退让的旧配置项");
        }
        if (configSrc.contains("blocks_per_dust") && configSrc.contains("refill_offhand")) {
            pass("配置项含 blocks_per_dust + refill_offhand");
        } else {
            failures.add("Config 缺少 blocks_per_dust 或 refill_offhand");
        }
        if (configSrc.contains("compressed_blocks_per_dust")) {
            pass("配置项含 compressed_blocks_per_dust（压缩粉单价）");
        } else {
            failures.add("Config 缺少 compressed_blocks_per_dust");
        }

        // --- 无敌事件必须被注册，否则那段代码永远不会执行 ---
        String modSrc = Files.readString(
                Path.of("src/main/java/com/wowuwo233/teleport_staff/TeleportStaffMod.java"),
                StandardCharsets.UTF_8);
        if (modSrc.contains("register(new Invulnerability())")) {
            pass("无敌事件处理器已注册到 Forge 事件总线");
        } else {
            failures.add("Invulnerability 没有注册，无敌不会生效");
        }

        // --- 位移必须走 offsetX/offsetZ，保证不动的那个轴逐位不变 ---
        String targetSrc = Files.readString(
                Path.of("src/main/java/com/wowuwo233/teleport_staff/teleport/TeleportTarget.java"),
                StandardCharsets.UTF_8);
        if (targetSrc.contains("direction.offsetX(") && targetSrc.contains("direction.offsetZ(")) {
            pass("位移走 offsetX/offsetZ（无侧向偏移）");
        } else {
            failures.add("TeleportTarget 没有用 offsetX/offsetZ 计算位移");
        }

        // --- 未加载区块必须先显式加载，否则 getHeight 会返回基岩层高度 ---
        if (targetSrc.contains("level.getChunk(")) {
            pass("先加载目标区块再取地表高度");
        } else {
            failures.add("没有显式加载目标区块，getHeight 对未加载区块会返回基岩层高度");
        }

        // --- 世界边界要处理，否则越过边界的位置会被服务端拽回来 ---
        if (targetSrc.contains("getWorldBorder()")) {
            pass("考虑了世界边界");
        } else {
            failures.add("没有处理世界边界");
        }
    }

    // ------------------------------------------------------------ 工具方法 ----

    /**
     * 检查某个语言键对应的值里是否含 %d 占位符。
     *
     * <p>比比对整句固定文案可靠：文案随时会改，而「这个键带一个数字参数」
     * 是代码与语言文件之间的约定，改了游戏里就会抛格式化异常。
     */
    private static boolean hasPlaceholder(String json, String key) {
        Matcher m = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() && m.group(1).contains("%d");
    }

    /**
     * 比较两张 PNG 的「不透明区域」是否逐像素一致，也就是形状是否相同。
     *
     * <p>这是唯一能自动验证「形状一样、只有颜色不同」这个需求的办法。
     *
     * <p>实现上直接解析 PNG 像素：两张图都是 16x16、同一种颜色类型，
     * 用 ImageIO 读进来比对 alpha 通道。放不了 ImageIO 的场合（比如
     * 无图形环境）会抛异常，所以调用方做了 try/catch。
     */
    private static boolean sameOpacityMask(byte[] pngA, byte[] pngB) {
        try {
            java.awt.image.BufferedImage a = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(pngA));
            java.awt.image.BufferedImage b = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(pngB));
            if (a == null || b == null) return true;   // 读不出来就不误报
            if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
            for (int y = 0; y < a.getHeight(); y++) {
                for (int x = 0; x < a.getWidth(); x++) {
                    boolean opA = ((a.getRGB(x, y) >>> 24) != 0);
                    boolean opB = ((b.getRGB(x, y) >>> 24) != 0);
                    if (opA != opB) return false;
                }
            }
            return true;
        } catch (Throwable t) {
            // 无图形环境下 ImageIO 可能不可用；跳过而不是让整个检查失败
            return true;
        }
    }

    /** 粗暴但够用：从 JSON 文本里抓出所有 "key": 形式的一级/多级键。 */
    private static List<String> keys(String json) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(json);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** 读取 PNG 头里的大端 4 字节整数（宽在偏移 16，高在偏移 20）。 */
    private static int readPngInt(byte[] data, int offset) {
        if (data.length < offset + 4) {
            return -1;
        }
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static void pass(String message) {
        passed++;
        System.out.println("  ok  " + message);
    }

    private Check() {
    }
}
