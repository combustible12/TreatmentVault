package com.menter.treatmentvault.importer

import com.menter.treatmentvault.data.AppDao
import com.menter.treatmentvault.data.SupplyEntity

object MementoCsv {
    fun parseLine(line:String):List<String>{ val out= mutableListOf<String>(); val cur=StringBuilder(); var q=false; var i=0; while(i<line.length){ val c=line[i]; when{ c=='"' && q && i+1<line.length && line[i+1]=='"'->{cur.append('"');i++}; c=='"'->q=!q; c==','&&!q->{out+=cur.toString();cur.clear()}; else->cur.append(c)};i++};out+=cur.toString();return out }
    suspend fun import(text:String,dao:AppDao):Int{ val lines=text.lines().filter{it.isNotBlank()}; if(lines.size<2)return 0; val h=parseLine(lines.first()); val idx=h.withIndex().associate{it.value.trim() to it.index}; fun List<String>.v(k:String)=getOrNull(idx[k]?:-1)?.trim().orEmpty(); var n=0
        lines.drop(1).forEach{ val r=parseLine(it); val name=r.v("名称"); if(name.isNotBlank()){ val old=dao.supplyByName(name); dao.saveSupply(SupplyEntity(id=old?.id?:0,name=name,stages=r.v("使用阶段"),purpose=r.v("作用"),necessity=r.v("必要性").ifBlank{"按需购买"},purchaseStatus=r.v("购买状态").ifBlank{r.v("已购").ifBlank{"待购"}},quantity=r.v("数量").toIntOrNull(),price=r.v("价格").toDoubleOrNull(),purchaseUrl=r.v("购买链接"),notes=r.v("备注"))); n++ } }; return n }
}
