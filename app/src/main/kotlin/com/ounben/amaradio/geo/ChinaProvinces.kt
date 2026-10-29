package com.ounben.amaradio.geo

/**
 * Offline mapping for China province/city resolution.
 *
 * radio-browser stores Chinese provinces with legacy romanizations
 * (Wade-Giles / postal: Hupei, Kwangtung, Honan ...) and inconsistently
 * (Zhejiang vs Chekiang). This table maps every known variant
 * (verified against the live radio-browser CN dataset, 2179 stations,
 * 66 distinct state values) to a canonical province key.
 *
 * City-level grouping works by station-NAME keyword matching because
 * radio-browser has no city field for CN stations.
 */
object ChinaProvinces {

    /** Canonical province key (used in prefs and UI labels). */
    data class Province(
        val key: String,          // canonical Chinese name, e.g. "湖北"
        val aliases: Set<String>, // all lowercase state-string variants
        val cities: List<String>  // display city names, doubles as name-match keywords
    )

    /** Lowercased state-string -> province key. */
    val stateToProvince: Map<String, String>

    /** province key -> Province */
    val byKey: Map<String, Province>

    /** All provinces in display order (roughly geographic). */
    val all: List<Province>

    init {
        val provinces = listOf(
            Province("北京", setOf("beijing", "pei-ching", "北京市"),
                listOf("北京")),
            Province("天津", setOf("tientsin", "tianjin", "天津市"),
                listOf("天津")),
            Province("河北", setOf("hopei", "hebei", "河北", "河北省"),
                listOf("石家庄", "唐山", "保定", "邯郸", "秦皇岛", "沧州", "廊坊", "承德", "张家口", "衡水", "邢台")),
            Province("山西", setOf("shansi", "shanxi", "山西", "山西省"),
                listOf("太原", "大同", "阳泉", "长治", "晋城", "朔州", "运城", "临汾", "忻州", "吕梁", "晋中")),
            Province("内蒙古", setOf("inner mongolia", "nei mongol", "内蒙古", "内蒙古自治区", "neimenggu"),
                listOf("呼和浩特", "包头", "鄂尔多斯", "赤峰", "呼伦贝尔", "通辽", "乌兰察布", "巴彦淖尔", "乌海")),
            Province("辽宁", setOf("liaoning", "辽宁", "辽宁省", "fengtien", "dalian", "大连"),
                listOf("沈阳", "大连", "鞍山", "抚顺", "本溪", "丹东", "锦州", "营口", "阜新", "辽阳", "盘锦", "铁岭", "朝阳", "葫芦岛")),
            Province("吉林", setOf("jilin", "kirin", "吉林", "吉林省"),
                listOf("长春", "吉林市", "四平", "辽源", "通化", "白山", "松原", "白城", "延边")),
            Province("黑龙江", setOf("heilongjiang", "amur river", "黑龙江", "黑龙江省", "heilingkiang"),
                listOf("哈尔滨", "齐齐哈尔", "大庆", "牡丹江", "佳木斯", "绥化", "鸡西", "鹤岗", "双鸭山", "伊春", "七台河", "黑河")),
            Province("上海", setOf("shanghai", "上海", "上海市", "shanghai direct administered municipality"),
                listOf("上海")),
            Province("江苏", setOf("kiangsu", "jiangsu", "江苏", "江苏省"),
                listOf("南京", "苏州", "无锡", "常州", "南通", "徐州", "扬州", "镇江", "盐城", "泰州", "淮安", "连云港", "宿迁")),
            Province("浙江", setOf("chekiang", "zhejiang", "浙江", "浙江省"),
                listOf("杭州", "宁波", "温州", "绍兴", "嘉兴", "湖州", "金华", "台州", "丽水", "衢州", "舟山")),
            Province("安徽", setOf("anhwei", "anhui", "安徽", "安徽省"),
                listOf("合肥", "芜湖", "蚌埠", "淮南", "马鞍山", "安庆", "阜阳", "滁州", "六安", "宿州", "铜陵", "宣城", "黄山", "池州", "亳州", "淮北")),
            Province("福建", setOf("fukien", "fujian", "福建", "福建省"),
                listOf("福州", "厦门", "泉州", "漳州", "莆田", "三明", "南平", "龙岩", "宁德")),
            Province("江西", setOf("kiangsi", "jiangxi", "江西", "江西省"),
                listOf("南昌", "赣州", "九江", "上饶", "抚州", "宜春", "吉安", "景德镇", "萍乡", "新余", "鹰潭")),
            Province("山东", setOf("shantung", "shandong", "山东", "山东省"),
                listOf("济南", "青岛", "烟台", "潍坊", "临沂", "淄博", "济宁", "泰安", "威海", "日照", "德州", "聊城", "滨州", "菏泽", "枣庄", "东营", "安丘")),
            Province("河南", setOf("honan", "henan", "河南", "河南省"),
                listOf("郑州", "洛阳", "开封", "南阳", "新乡", "许昌", "焦作", "平顶山", "信阳", "商丘", "周口", "驻马店", "安阳", "鹤壁", "濮阳", "漯河", "三门峡")),
            Province("湖北", setOf("hupei", "hubei", "湖北", "湖北省"),
                listOf("武汉", "宜昌", "襄阳", "荆州", "黄石", "十堰", "孝感", "荆门", "鄂州", "黄冈", "咸宁", "随州", "恩施", "仙桃", "潜江", "天门", "汉川", "黄梅", "江夏", "团风", "公安")),
            Province("湖南", setOf("hunan", "湖南", "湖南省", "huna", "hun"),
                listOf("长沙", "株洲", "湘潭", "衡阳", "岳阳", "常德", "郴州", "邵阳", "益阳", "娄底", "永州", "怀化", "张家界", "湘西")),
            Province("广东", setOf("kwangtung", "guangdong", "广东", "广东省"),
                listOf("广州", "深圳", "东莞", "佛山", "珠海", "中山", "惠州", "江门", "汕头", "湛江", "肇庆", "茂名", "韶关", "梅州", "汕尾", "河源", "阳江", "清远", "潮州", "揭阳", "云浮", "顺德", "怀集")),
            Province("广西", setOf("kwangsi", "guangxi", "广西", "广西壮族自治区"),
                listOf("南宁", "柳州", "桂林", "玉林", "北海", "梧州", "贵港", "钦州", "百色", "河池", "贺州", "来宾", "崇左", "防城港")),
            Province("海南", setOf("hainan", "海南", "海南省"),
                listOf("海口", "三亚", "儋州", "琼海", "文昌", "万宁", "五指山")),
            Province("重庆", setOf("chungking", "chongqing", "重庆", "重庆市"),
                listOf("重庆")),
            Province("四川", setOf("szechuan", "sichuan", "四川", "四川省"),
                listOf("成都", "绵阳", "德阳", "宜宾", "南充", "泸州", "达州", "自贡", "乐山", "内江", "遂宁", "攀枝花", "眉山", "广安", "资阳", "雅安", "广元", "巴中", "阿坝", "甘孜", "凉山")),
            Province("贵州", setOf("kweichow", "guizhou", "贵州", "贵州省"),
                listOf("贵阳", "遵义", "六盘水", "安顺", "毕节", "铜仁", "黔南", "黔东南", "黔西南")),
            Province("云南", setOf("yunnan", "云南", "云南省"),
                listOf("昆明", "曲靖", "玉溪", "大理", "红河", "文山", "楚雄", "普洱", "保山", "昭通", "丽江", "临沧", "西双版纳", "德宏", "迪庆", "怒江")),
            Province("西藏", setOf("xizang", "tibet", "西藏", "西藏自治区"),
                listOf("拉萨", "日喀则", "昌都", "林芝", "山南", "那曲", "阿里")),
            Province("陕西", setOf("shensi", "shaanxi", "陕西", "陕西省"),
                listOf("西安", "宝鸡", "咸阳", "渭南", "汉中", "延安", "榆林", "安康", "商洛", "铜川")),
            Province("甘肃", setOf("kansu", "gansu", "甘肃", "甘肃省"),
                listOf("兰州", "天水", "酒泉", "嘉峪关", "庆阳", "平凉", "白银", "武威", "张掖", "陇南", "定西", "金昌", "临夏", "甘南")),
            Province("青海", setOf("tsinghai", "qinghai", "青海", "青海省"),
                listOf("西宁", "海东", "海西", "海北", "海南州", "黄南", "果洛", "玉树")),
            Province("宁夏", setOf("ningsia", "ningxia", "宁夏", "宁夏回族自治区"),
                listOf("银川", "石嘴山", "吴忠", "固原", "中卫")),
            Province("新疆", setOf("sinkiang", "xinjiang", "新疆", "新疆维吾尔自治区"),
                listOf("乌鲁木齐", "克拉玛依", "喀什", "伊犁", "阿克苏", "巴音郭楞", "昌吉", "哈密", "和田", "塔城", "阿勒泰", "博尔塔拉", "克孜勒苏", "石河子")),
            Province("香港", setOf("hong kong", "香港", "taiwan hongkong"),
                listOf("香港")),
            Province("澳门", setOf("macao", "macau", "澳门"),
                listOf("澳门")),
            Province("台湾", setOf("taiwan", "台湾"),
                listOf("台北", "高雄", "台中", "台南"))
        )

        all = provinces
        byKey = provinces.associateBy { it.key }
        val map = HashMap<String, String>()
        provinces.forEach { p ->
            map[p.key] = p.key
            p.aliases.forEach { a -> map[a] = p.key }
        }
        stateToProvince = map
    }

    /**
     * Resolve a radio-browser state string (any language/casing) to a canonical province key.
     * Returns null for junk values like "Music" or "Maule".
     */
    fun provinceFromState(state: String?): String? {
        if (state.isNullOrBlank()) return null
        return stateToProvince[state.trim().lowercase()]
    }

    /**
     * Find the province that contains [cityName] in its city list.
     * Exact prefix match: "武汉市" -> "武汉" -> 湖北.
     */
    fun provinceFromCity(cityName: String?): String? {
        if (cityName.isNullOrBlank()) return null
        val name = cityName.trim()
        all.forEach { p ->
            if (p.cities.any { name.startsWith(it) || it.startsWith(name) }) return p.key
        }
        return null
    }

    /** Districts / county-level cities whose stations belong to the given city's group. */
    private val cityDistricts: Map<String, List<String>> = mapOf(
        "武汉" to listOf("江夏", "汉阳", "黄陂", "蔡甸", "新洲", "东西湖", "汉南", "洪山", "武昌", "汉口", "青山", "江岸", "江汉", "硚口", "长江"),
        "北京" to listOf("海淀", "朝阳", "丰台", "通州", "昌平", "大兴", "顺义", "房山", "怀柔", "密云", "延庆", "门头沟", "平谷"),
        "上海" to listOf("浦东", "徐汇", "闵行", "宝山", "松江", "嘉定", "青浦", "奉贤", "金山", "崇明"),
        "广州" to listOf("番禺", "花都", "增城", "从化", "天河", "越秀", "白云", "黄埔", "南沙"),
        "深圳" to listOf("南山", "福田", "宝安", "龙岗", "龙华", "罗湖", "盐田", "坪山"),
        "成都" to listOf("双流", "温江", "郫都", "龙泉驿", "都江堰", "彭州", "邛崃", "崇州", "简阳", "金堂", "新都"),
        "重庆" to listOf("渝中", "江北", "沙坪坝", "九龙坡", "南岸", "北碚", "渝北", "巴南", "万州", "涪陵"),
        "杭州" to listOf("萧山", "余杭", "富阳", "临安", "桐庐", "淳安", "建德", "西湖", "滨江"),
        "南京" to listOf("江宁", "浦口", "六合", "溧水", "高淳", "栖霞", "雨花台")
    )

    /**
     * Name keywords for the city-level group: the city, its districts and
     * county-level cities. Municipality provinces fall back to the province itself.
     */
    fun cityKeywordsFor(provinceKey: String, cityName: String?): List<String> {
        val p = byKey[provinceKey] ?: return emptyList()
        val c = cityName?.trim().orEmpty().removeSuffix("市")
        val base = when {
            c.isNotEmpty() -> c
            p.key in setOf("北京", "上海", "天津", "重庆") -> p.key
            else -> p.cities.firstOrNull() ?: return emptyList()
        }
        return listOf(base) + (cityDistricts[base] ?: emptyList())
    }

    /** Other cities of the province (used to exclude out-of-town stations from the provincial group). */
    fun otherCityKeywords(provinceKey: String, cityName: String?): List<String> {
        val p = byKey[provinceKey] ?: return emptyList()
        val c = cityName?.trim().orEmpty().removeSuffix("市")
        return p.cities.filter { it != c }
    }
}
