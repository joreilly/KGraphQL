package com.github.pgutkowski.kgraphql.request

import com.github.pgutkowski.kgraphql.SyntaxException

val DELIMITERS = "{}\n(): "

val IGNORED_CHARACTERS = "\n\t, "

val OPERANDS = "{}():"

fun tokenizeRequest(input : String) : List<String> {
    var i = 0
    val tokens : MutableList<String> = mutableListOf()

    while(i < input.length){
        when(input[i]){
            in IGNORED_CHARACTERS -> { i++ }
            in OPERANDS -> {
                tokens.add(input[i].toString())
                i++
            }
            '\"' -> {
                val token = input.substring(i+1).takeWhile { it != '\"' }
                i += token.length + 2 //2 for quotes
                tokens.add("\"$token\"")
            }
            else -> {
                val tokenBuilder = StringBuilder()

                while(i < input.length && input[i] !in DELIMITERS){
                    if(input[i] !in IGNORED_CHARACTERS) tokenBuilder.append(input[i])
                    i++
                }

                if(tokenBuilder.isNotBlank()) tokens.add(tokenBuilder.toString())
            }
        }
    }

    return tokens
}

fun createDocumentTokens(tokens : List<String>) : DocumentTokens {
    val operations : MutableList<DocumentTokens.OperationTokens> = mutableListOf()
    val fragments : MutableList<DocumentTokens.FragmentTokens> = mutableListOf()

    var index = 0
    while(index < tokens.size){
        val token = tokens[index]

        if(token == "fragment"){
            val (endIndex, fragmentTokens) = createFragmentTokens(tokens, index)
            index = endIndex
            fragments.add(fragmentTokens)
        } else {
            val (endIndex, operationTokens) = createOperationTokens(tokens, index)
            index = endIndex
            operations.add(operationTokens)
        }
    }

    return DocumentTokens(fragments, operations)
}

fun createFragmentTokens(tokens : List<String>, startIndex: Int) : Pair<Int, DocumentTokens.FragmentTokens>{
    var index = startIndex
    var name : String? = null
    var typeCondition : String? = null
    while(index < tokens.size){
        val token = tokens[index]
        when(token) {
            "fragment" -> {
                name = tokens[index + 1]
                index++
            }
            "on" -> {
                typeCondition = tokens[index + 1]
                index++
            }
            "{" -> {
                val indexOfClosingBracket = indexOfClosingBracket(tokens, index)
                if(name == null) throw SyntaxException("Invalid anonymous external fragment")
                return indexOfClosingBracket to DocumentTokens.FragmentTokens(name, typeCondition, tokens.subList(index, indexOfClosingBracket))
            }
            else -> throw SyntaxException("Unexpected token: $token")
        }
        index++
    }
    throw SyntaxException("Invalid fragment $name declaration without selection set")
}

fun createOperationTokens(tokens : List<String>, startIndex: Int) : Pair<Int, DocumentTokens.OperationTokens>{
    var index = startIndex
    var name : String? = null
    var type : String? = null
    while(index < tokens.size){
        val token = tokens[index]
        when {
            token == "{" -> {
                val indexOfClosingBracket = indexOfClosingBracket(tokens, index)
                return indexOfClosingBracket to DocumentTokens.OperationTokens(name, type, tokens.subList(index, indexOfClosingBracket))
            }
            type == null -> {
                if(token.equals("query", true) || token.equals("mutation", true)){
                    type = token
                } else {
                    throw SyntaxException("Unexpected operation type $token")
                }
            }
            name == null -> name = token
            else -> throw SyntaxException("Unexpected token: $token")
        }
        index++
    }
    throw SyntaxException("Invalid operation $name without selection set")
}

fun indexOfClosingBracket(tokens: List<String>, startIndex: Int) : Int {
    var nestedBrackets = 0
    val subList = tokens.subList(startIndex, tokens.size)
    subList.forEachIndexed { index, token ->
        when(token){
            "{" -> nestedBrackets++
            "}" -> nestedBrackets--
        }
        if(nestedBrackets == 0) return index + startIndex + 1
    }
    val indexOfTokenInString = getIndexOfTokenInString(tokens.subList(0, startIndex))
    throw SyntaxException("Missing closing bracket for opening bracket at $indexOfTokenInString")
}

fun getIndexOfTokenInString(tokens: List<String>): Int {
    return tokens.fold(0, { index, token -> index + token.length })
}