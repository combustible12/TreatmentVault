package com.menter.treatmentvault.importer

data class UpdatePackage(val version:Int=1,val packageId:String="",val createdAt:String="",val items:List<UpdateItem> = emptyList())
data class UpdateItem(val type:String="",val action:String="upsert",val data:Map<String,Any?> = emptyMap())
data class ImportPreview(val packageId:String,val fileName:String,val counts:Map<String,Int>,val updatePackage:UpdatePackage)
data class ImportResult(val added:Int,val updated:Int,val failed:Int,val messages:List<String>)
